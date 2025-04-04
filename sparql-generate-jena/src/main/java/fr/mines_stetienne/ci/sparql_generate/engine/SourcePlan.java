/*
 * Copyright 2016 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.mines_stetienne.ci.sparql_generate.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.jena.atlas.web.TypedInputStream;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.SysRIOT;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.mines_stetienne.ci.sparql_generate.SPARQLExtException;
import fr.mines_stetienne.ci.sparql_generate.stream.LookUpRequest;
import fr.mines_stetienne.ci.sparql_generate.stream.SPARQLExtStreamManager;
import fr.mines_stetienne.ci.sparql_generate.utils.LogUtils;

/**
 * Executes a <code>{@code SOURCE <node> ACCEPT <mime> AS <var>}</code> clause.
 *
 * @author Maxime Lefrançois
 */
public class SourcePlan extends BindOrSourcePlan {

	/**
	 * The logger.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(SourcePlan.class);

	/**
	 * The source node. A uri or a variable.
	 */
	private final Node node;

	/**
	 * The accept node. A uri or a variable.
	 */
	private final Node accept;

	/**
	 * type mapper.
	 */
	private final static TypeMapper tm = TypeMapper.getInstance();
	
	public static final Pattern pattern = Pattern.compile("^https?://www\\.iana\\.org/assignments/media-types/(?<mediatype>.*)$");


	/**
	 * The generation plan of a <code>{@code SOURCE <node> ACCEPT <mime> AS
	 * <var>}</code> clause.
	 *
	 * @param node   The IRI or the Variable node where a GET must be operated. Must
	 *               not be null.
	 * @param accept The IRI or the Variable node that represent the accepted
	 *               Internet Media Type. May be null.
	 * @param var    The variable to bound the potentially retrieved document. Must
	 *               not be null.
	 */
	public SourcePlan(final Node node, final Node accept, final Var var) {
		super(var);
		Objects.requireNonNull(node, "Node must not be null");
		Objects.requireNonNull(var, "Var must not be null");
		if (!node.isURI() && !node.isVariable()) {
			throw new IllegalArgumentException("Source node must be a IRI or a" + " Variable. got " + node);
		}
		this.node = node;
		this.accept = accept;
	}

	final protected Binding exec(final Binding binding, final Context context) {

		LOG.debug("Start " + this);
		Objects.nonNull(binding);
		// generate the source URI.
		final String sourceUri = getActualSource(binding);
		final String acceptHeader = getAcceptHeader(binding);
		LOG.trace("... resolved to SOURCE <" + sourceUri + "> ACCEPT " + acceptHeader + " AS " + var);
		if (sourceUri == null) {
			return BindingFactory.binding(binding, var, null);
		}
		final LookUpRequest request = new LookUpRequest(sourceUri, acceptHeader);
		final SPARQLExtStreamManager sm = (SPARQLExtStreamManager) context.get(SysRIOT.sysStreamManager);
		Objects.requireNonNull(sm);
		try (TypedInputStream stream = sm.open(request)) {
			if (stream == null) {
				LOG.info(
						"Exec SOURCE <" + sourceUri + "> ACCEPT " + acceptHeader + " AS " + var + " returned nothing.");
				return BindingFactory.binding(binding);
			}
			try (InputStream in = stream.getInputStream()) {
				final String literal = IOUtils.toString(in, "UTF-8");
				final RDFDatatype dt;
				if (stream.getMediaType() != null && stream.getMediaType().getContentTypeStr() != null) {
					dt = tm.getSafeTypeByName(
							"https://www.iana.org/assignments/media-types/" + stream.getMediaType().getContentTypeStr());
				} else {
					dt = tm.getSafeTypeByName("http://www.w3.org/2001/XMLSchema#string");
				}
				final Node n = NodeFactory.createLiteral(literal, dt);
				LOG.debug("Exec " + this + " returned. " + "Enable TRACE level for more.");
				if (LOG.isTraceEnabled()) {
					LOG.trace("Exec " + this + " returned\n" + LogUtils.compress(n));
				}
				return BindingFactory.binding(binding, var, n);
			}
		} catch (IOException | DatatypeFormatException ex) {
			LOG.warn("Exception while looking up " + sourceUri + ":", ex);
			return BindingFactory.binding(binding);
		}

	}

	/**
	 *
	 * @param binding -
	 * @return the actual URI that represents the location of the query to be
	 *         fetched.
	 */
	private String getActualSource(final Binding binding) {
		if (node.isVariable()) {
			Node actualSource = binding.get((Var) node);
			if (actualSource == null) {
				return null;
			}
			if (!actualSource.isURI()) {
				throw new SPARQLExtException("Variable " + node.getName()
						+ " must be bound to a IRI that represents the location" + " of the query to be fetched.");
			}
			return actualSource.getURI();
		} else {
			if (!node.isURI()) {
				throw new SPARQLExtException("The source must be a IRI"
						+ " that represents the location of the query to be" + " fetched. Got " + node.getURI());
			}
			return node.getURI();
		}
	}

	/**
	 * returns the accept header computed from ACCEPT clause.
	 *
	 * @param binding -
	 * @return the actual accept header to use.
	 */
	private String getAcceptHeader(final Binding binding) {
		Node actualAccept = accept;
		if (accept == null) {
			return "*/*";
		}
		if (accept.isVariable()) {
			actualAccept = binding.get((Var) accept);
			if (accept == null) {
				return "*/*";
			}
		}
		if (!actualAccept.isURI()) {
			throw new SPARQLExtException("ACCEPT must be bound to a IRI that represents the internet"
					+ " media type of the source to be fetched. For"
					+ " instance, <https://www.iana.org/assignments/media-types/application/xml>.");
		}
		Matcher matcher = pattern.matcher(actualAccept.getURI());
		if(!matcher.find()) {
			throw new SPARQLExtException("ACCEPT " + actualAccept.getURI() + " must be a IANA MIME URN (RFC to be"
					+ " written). For instance,  <https://www.iana.org/assignments/media-types/application/xml> or  <https://www.iana.org/assignments/media-types/application/xml>.");
		}
		return matcher.group("mediatype");
	}

	@Override
	public String toString() {
		return "SOURCE " + node + (accept != null ? " ACCEPT " + accept : "") + " AS " + var;
	}

}
