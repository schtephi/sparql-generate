/*
 * Copyright 2020 MINES Saint-Étienne
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
package fr.emse.ci.sparqlext.function.library;

import fr.emse.ci.sparqlext.utils.ContextUtils;
import fr.emse.ci.sparqlext.utils.ST;

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.sparql.ARQInternalErrorException;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.NodeValueString;
import org.apache.jena.sparql.function.Function;
import org.apache.jena.sparql.function.FunctionEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maxime Lefrançois
 */
public class ST_Decr implements Function {

    private static final Logger LOG = LoggerFactory.getLogger(ST_Decr.class);

    public static String URI = ST.decr;

    private static NodeValue EMPTY_NODE = new NodeValueString("");

    @Override
    public final void build(String uri, ExprList args) {
        if (args.size() != 0) {
            throw new ExprEvalException("Expecting zero argument");
        }
    }

    @Override
    public NodeValue exec(Binding binding, ExprList args, String uri, FunctionEnv env) {
        if (args == null) {
            throw new ARQInternalErrorException("FunctionBase: Null args list");
        }
        if (args.size() != 0) {
            throw new ExprEvalException("Expecting zero argument");
        }
        IndentedWriter writer = ContextUtils.getTemplateOutput(env.getContext());
        if(writer != null) {
        	writer.decIndent();
        } else {
        	LOG.warn("calling st:decr() outside TEMPLATE context.");
        }
        return EMPTY_NODE;
    }

}
