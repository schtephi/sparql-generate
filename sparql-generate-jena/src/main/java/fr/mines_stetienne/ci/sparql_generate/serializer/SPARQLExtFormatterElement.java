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
package fr.mines_stetienne.ci.sparql_generate.serializer;

import static org.apache.jena.sparql.serializer.FormatterElement.INDENT;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryVisitor;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.PathBlock;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.NodeValueString;
import org.apache.jena.sparql.serializer.QuerySerializer;
import org.apache.jena.sparql.serializer.QuerySerializerFactory;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.serializer.SerializerRegistry;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.Element1;
import org.apache.jena.sparql.syntax.ElementAssign;
import org.apache.jena.sparql.syntax.ElementBind;
import org.apache.jena.sparql.syntax.ElementData;
import org.apache.jena.sparql.syntax.ElementDataset;
import org.apache.jena.sparql.syntax.ElementExists;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementLateral;
import org.apache.jena.sparql.syntax.ElementMinus;
import org.apache.jena.sparql.syntax.ElementNamedGraph;
import org.apache.jena.sparql.syntax.ElementNotExists;
import org.apache.jena.sparql.syntax.ElementOptional;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.jena.sparql.syntax.ElementService;
import org.apache.jena.sparql.syntax.ElementSubQuery;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;
import org.apache.jena.sparql.syntax.ElementUnion;
import org.apache.jena.sparql.util.FmtUtils;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.mines_stetienne.ci.sparql_generate.SPARQLExt;
import fr.mines_stetienne.ci.sparql_generate.query.SPARQLExtQuery;
import fr.mines_stetienne.ci.sparql_generate.query.SPARQLExtQueryVisitor;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementBox;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementExpr;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementFormat;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementGenerateTriplesBlock;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementIterator;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementPerform;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementSource;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementSubExtQuery;
import fr.mines_stetienne.ci.sparql_generate.syntax.ElementTGroup;
import fr.mines_stetienne.ci.sparql_generate.syntax.SPARQLExtElementVisitor;

/**
 * Extends the ARQ Element Formatter with SPARQL-Generate elements.
 *
 * @author Maxime Lefrançois
 */
public class SPARQLExtFormatterElement extends SPARQLExtFormatterBase implements SPARQLExtElementVisitor {

    private static final Logger LOG
            = LoggerFactory.getLogger(SPARQLExtFormatterElement.class);

    /**
     * Control whether to show triple pattern boundaries - creates extra nesting
     */
    public static final boolean PATTERN_MARKERS = false;

//    /** Control whether triple patterns always have a final dot - it can be dropped in some cases */
//    public static boolean PATTERN_FINAL_DOT = false ;
    /**
     * Control whether (non-triple) patterns have a final dot - it can be
     * dropped
     */
    public static final boolean GROUP_SEP_DOT = false;

    /**
     * Control whether the first item of a group is on the same line as the {
     */
    public static final boolean GROUP_FIRST_ON_SAME_LINE = true;

    /**
     * Control pretty printing
     */
    public static final boolean PRETTY_PRINT = true;

    /**
     * Control whether disjunction has set of delimiters - as it's a group
     * usually, these aren't needed
     */
    public static final boolean UNION_MARKERS = false;

    /**
     * Control whether GRAPH indents in a fixed way or based on the layout size
     */
    public static final boolean GRAPH_FIXED_INDENT = true;

    /**
     * Control whether NOT EXIST/EXISTS indents in a fixed way or based on the
     * layout size
     */
    public static final boolean ELEMENT1_FIXED_INDENT = true;

    /**
     * Control triples pretty printing
     */
    public static final int TRIPLES_SUBJECT_COLUMN = 8;

    // Less than this => rest of triple on the same line
    // Could be smart and make it depend on the property length as well.  Later.
    public static final int TRIPLES_SUBJECT_LONG = 12;

    public static final int TRIPLES_PROPERTY_COLUMN = 20;

    public static final int TRIPLES_COLUMN_GAP = 2;

    public final int BLOCK_INDENT = 2;

    public SPARQLExtFormatterElement(IndentedWriter out, SerializationContext context) {
        super(out, context);
    }

    public boolean topMustBeGroup() {
        return false;
    }

    @Override
    public void visit(ElementGenerateTriplesBlock el) {
        if (el.isEmpty()) {
            out.println("# Empty BGGP");
            return;
        }
        formatTriples(el.getPattern());
    }

    @Override
    public void visit(ElementSubExtQuery el) {
        SPARQLExtQuery q = el.getQuery();
        if (!q.isGenerateType() && !q.isTemplateType() && !q.isPerformType()) {
            throw new IllegalArgumentException("Sub-query is expected to be a generate, template, or perform query");
        }
        out.incIndent(INDENT);
        QuerySerializerFactory factory = SerializerRegistry.get().getQuerySerializerFactory(SPARQLExt.SYNTAX);
        SPARQLExtQueryVisitor visitor = (SPARQLExtQueryVisitor) factory.create(SPARQLExt.SYNTAX, new SerializationContext(q.getPrologue()), out);

        visitor.startVisit(q);
        if (q.isGenerateType()) {
            visitor.visitGenerateClause(q);
        } else if (q.isTemplateType()) {
            visitor.visitTemplateClause(q);
        } else if (q.isPerformType()) {
            visitor.visitPerformClause(q);
        }
        visitor.visitDatasetDecl(q);
        visitor.visitBindingClauses(q);
        visitor.visitQueryPattern(q);
        visitor.visitGroupBy(q);
        visitor.visitHaving(q);
        visitor.visitOrderBy(q);
        visitor.visitOffset(q);
        visitor.visitLimit(q);
        visitor.visitPostSelect(q);
        visitor.visitValues(q);
        visitor.finishVisit(q);

        out.print(" .");
        out.decIndent(INDENT);
        out.newline();
    }

    @Override
    public void visit(ElementIterator el) {
        SPARQLExtFmtExprSPARQL v = new SPARQLExtFmtExprSPARQL(out, context);
        out.print("ITERATOR ");
        v.format(el.getExpr());
        out.print(" AS");
        for (Var var : el.getVars()) {
            out.print(" " + var);
        }
    }

    @Override
    public void visit(ElementSource el) {
        out.print("SOURCE ");
        SPARQLExtFmtUtils.printNode(out, el.getSource(), context);
        if (el.getAccept() != null) {
            out.print(" ACCEPT <" + el.getAccept() + ">");
        }
        out.print(" AS " + el.getVar());
    }

    @Override
    public void visit(ElementExpr el) {
        SPARQLExtFmtExprSPARQL v = new SPARQLExtFmtExprSPARQL(out, context);
        v.format(el.getExpr());
    }

    @Override
    public void visit(ElementBox el) {
        out.print("BOX {");
        visitTexprs(el.getTExpressions());
        out.print("}");
    }

    @Override
    public void visit(ElementFormat el) {
        out.print("FORMAT ");
        out.print("{");
        out.incIndent(BLOCK_INDENT);
        out.newline();
        el.getExpr().visit(this);
        out.newline();
        visitTexprs(el.getTExpressions());
        out.decIndent(BLOCK_INDENT);
        out.newline();
        out.print("}");
    }

    @Override
    public void visit(ElementTGroup el) {
        out.print("GROUP ");
        if (el.isDistinct()) {
            out.print("DISTINCT ");
        }
        out.print("{");
        out.incIndent(BLOCK_INDENT);
        out.newline();
        visitTexprs(el.getTExpressions());
        if (el.getSeparator() != null) {
            SPARQLExtFmtExprSPARQL v = new SPARQLExtFmtExprSPARQL(out, context);
            NodeValue n = new NodeValueString(el.getSeparator());
            out.print("; SEPARATOR = ");
            v.format(n);
        }
        out.decIndent(BLOCK_INDENT);
        out.newline();
        out.print("}");
    }

    @Override
    public void visit(ElementPerform el) {
        out.print("PERFORM ");
        SPARQLExtFmtUtils.printNode(out, el.getName(), context);
        if (el.getParams() != null) {
            SPARQLExtFmtExprSPARQL v = new SPARQLExtFmtExprSPARQL(out, context);
            out.print("(");
            el.getParams().forEach((e) -> {
                v.format(e);
            });
            out.print(")");
        }
    }

    @Override
    public void visit(ElementTriplesBlock el) {
        if (el.isEmpty()) {
            out.println("# Empty BGP");
            return;
        }
        formatTriples(el.getPattern());
    }

    @Override
    public void visit(ElementPathBlock el) {
        // Write path block - don't put in a final trailing "." 
        if (el.isEmpty()) {
            out.println("# Empty BGP");
            return;
        }

        // Split into BGP-path-BGP-...
        // where the BGPs may be empty.
        PathBlock pBlk = el.getPattern();
        BasicPattern bgp = new BasicPattern();
        boolean first = true;      // Has anything been output?
        for (TriplePath tp : pBlk) {
            if (tp.isTriple()) {
                Triple t = tp.asTriple();
                Node s = t.getSubject();
                if(s.isVariable()) {
                    s = Var.alloc(Var.canonical(((Var)s).getVarName()));
                }
                Node p = t.getPredicate();
                Node o = t.getObject();
                if(o.isVariable()) {
                    o = Var.alloc(Var.canonical(((Var)o).getVarName()));
                }
                bgp.add(new Triple(s, p, o));
                continue;
            }

            if (!bgp.isEmpty()) {
                if (!first) {
                    out.println(" .");
                }
                flush(bgp);
                first = false;
            }
            if (!first) {
                out.println(" .");
            }
            // Path
            printSubject(tp.getSubject());
            out.print(" ");
            SPARQLExtPathWriter.write(out, tp.getPath(), context);
            out.print(" ");
            printObject(tp.getObject());
            first = false;
        }
        // Flush any stored triple patterns.
        if (!bgp.isEmpty()) {
            if (!first) {
                out.println(" .");
            }
            flush(bgp);
            first = false;
        }
    }

    private void flush(BasicPattern bgp) {
        formatTriples(bgp);
        bgp.getList().clear();
    }

    @Override
    public void visit(ElementDataset el) {
//        if ( el.getDataset() != null)
//        {
//            DatasetGraph dsNamed = el.getDataset() ;
//            out.print("DATASET ") ;
//            out.incIndent(INDENT) ;
//            Iterator iter = dsNamed.listNames() ;
//            if ( iter.hasNext() )
//            {
//                boolean first = false ;
//                for ( ; iter.hasNext() ; )
//                {
//                    if ( ! first )
//                        out.newline() ;
//                    out.print("FROM <") ;
//                    String s = (String)iter.next() ; 
//                    out.print(s) ;
//                    out.print(">") ;
//                }
//            }
//            out.decIndent(INDENT) ;
//            out.newline() ;
//        }
        if (el.getElement() != null) {
            visitAsGroup(el.getElement());
        }
    }

    @Override
    public void visit(ElementFilter el) {
        out.print("FILTER ");
        Expr expr = el.getExpr();
        SPARQLExtFmtExprSPARQL v = new SPARQLExtFmtExprSPARQL(out, context);

        // This assumes that complex expressions are bracketted
        // (parens) as necessary except for some cases:
        // Plain variable or constant
        boolean addParens = false;
        if (expr.isVariable()) {
            addParens = true;
        }
        if (expr.isConstant()) {
            addParens = true;
        }

        if (addParens) {
            out.print("( ");
        }
        v.format(expr);
        if (addParens) {
            out.print(" )");
        }
    }

    @Override
    public void visit(ElementAssign el) {
        out.print("LET (");
        out.print("?" + el.getVar().getVarName());
        out.print(" := ");
        SPARQLExtFmtExprSPARQL v = new SPARQLExtFmtExprSPARQL(out, context);
        v.format(el.getExpr());
        out.print(")");
    }

    @Override
    public void visit(ElementBind el) {
        out.print("BIND(");
        SPARQLExtFmtExprSPARQL v = new SPARQLExtFmtExprSPARQL(out, context);
        v.format(el.getExpr());
        out.print(" AS ");
        out.print("?" + el.getVar().getVarName());
        out.print(")");
    }

    @Override
    public void visit(ElementData el) {
        QuerySerializer.outputDataBlock(out, el.getVars(), el.getRows(), new SerializationContext(context.getPrologue()));
    }

    @Override
    public void visit(ElementUnion el) {
        if (el.getElements().size() == 1) {
            // If this is an element of just one, just do it inplace
            // Can't happen from a parsed query.
            // Now can :-)

            // SPARQL 1.1 inline UNION.
            // Same as OPTIONAL, MINUS
            out.print("UNION");
            out.incIndent(INDENT);
            out.newline();
            visitAsGroup(el.getElements().get(0));
            out.decIndent(INDENT);
            return;
        }

        if (UNION_MARKERS) {
            out.print("{");
            out.newline();
            out.pad();
        }

        out.incIndent(INDENT);

        boolean first = true;
        for (Element subElement : el.getElements()) {
            if (!first) {
                out.decIndent(INDENT);
                out.newline();
                out.print("UNION");
                out.newline();
                out.incIndent(INDENT);
            }
            visitAsGroup(subElement);
            first = false;
        }

        out.decIndent(INDENT);

        if (UNION_MARKERS) {
            out.newline();
            out.print("}");
        }
    }

    @Override
    public void visit(ElementGroup el) {
        out.print("{");
        int initialRowNumber = out.getRow();
        out.incIndent(INDENT);
        if (!GROUP_FIRST_ON_SAME_LINE) {
            out.newline();
        }

        int row1 = out.getRow();
        out.pad();

        boolean first = true;
        Element lastElt = null;

        for (Element subElement : el.getElements()) {
            // Some adjacent elements need a DOT:
            // ElementTriplesBlock, ElementPathBlock
            if (!first) {
                // Need to move on after the last thing printed.
                // Check for necessary DOT as separator
                if (GROUP_SEP_DOT || needsDotSeparator(lastElt, subElement)) {
                    out.print(" . ");
                }
                out.newline();
            }
            subElement.visit(this);
            first = false;
            lastElt = subElement;
        }
        out.decIndent(INDENT);

        // Where to put the closing "}"
        int row2 = out.getRow();
        if (row1 != row2) {
            out.newline();
        }

        // Finally, close the group.
        if (out.getRow() == initialRowNumber) {
            out.print(" ");
        }
        out.print("}");
    }

    private static boolean needsDotSeparator(Element el1, Element el2) {
        return needsDotSeparator(el1) && needsDotSeparator(el2);
    }

    private static boolean needsDotSeparator(Element el) {
        return (el instanceof ElementTriplesBlock) || (el instanceof ElementPathBlock);
    }

    @Override
    public void visit(ElementOptional el) {
        out.print("OPTIONAL");
        out.incIndent(INDENT);
        out.newline();
        visitAsGroup(el.getOptionalElement());
        out.decIndent(INDENT);
    }

    @Override
    public void visit(ElementLateral el) {
        out.print("LATERAL");
        out.incIndent(INDENT);
        out.newline();
        visitAsGroup(el.getLateralElement());
        out.decIndent(INDENT);
    }

    @Override
    public void visit(ElementNamedGraph el) {
        visitNodePattern("GRAPH", el.getGraphNameNode(), el.getElement());
    }

    @Override
    public void visit(ElementService el) {
        String x = "SERVICE";
        if (el.getSilent()) {
            x = "SERVICE SILENT";
        }
        visitNodePattern(x, el.getServiceNode(), el.getElement());
    }

    private void visitNodePattern(String label, Node node, Element subElement) {
        int len = label.length();
        out.print(label);
        out.print(" ");
        String nodeStr = (node == null) ? "*" : slotToString(node);
        out.print(nodeStr);
        len += nodeStr.length();
        if (GRAPH_FIXED_INDENT) {
            out.incIndent(INDENT);
            out.newline(); // NB and newline
        } else {
            out.print(" ");
            len++;
            out.incIndent(len);
        }
        visitAsGroup(subElement);

        if (GRAPH_FIXED_INDENT) {
            out.decIndent(INDENT);
        } else {
            out.decIndent(len);
        }
    }

    private void visitElement1(String label, Element1 el) {

        int len = label.length();
        out.print(label);
        len += label.length();
        if (ELEMENT1_FIXED_INDENT) {
            out.incIndent(INDENT);
            out.newline(); // NB and newline
        } else {
            out.print(" ");
            len++;
            out.incIndent(len);
        }
        visitAsGroup(el.getElement());
        if (ELEMENT1_FIXED_INDENT) {
            out.decIndent(INDENT);
        } else {
            out.decIndent(len);
        }
    }

    @Override
    public void visit(ElementExists el) {
        visitElement1("EXISTS", el);
    }

    @Override
    public void visit(ElementNotExists el) {
        visitElement1("NOT EXISTS", el);
    }

    @Override
    public void visit(ElementMinus el) {
        out.print("MINUS");
        out.incIndent(INDENT);
        out.newline();
        visitAsGroup(el.getMinusElement());
        out.decIndent(INDENT);
    }

    @Override
    public void visit(ElementSubQuery el) {
        out.print("{ ");
        out.incIndent(INDENT);
        Query q = el.getQuery();

        // Serialize with respect to the existing context
        QuerySerializerFactory factory = SerializerRegistry.get().getQuerySerializerFactory(SPARQLExt.SYNTAX);
        QueryVisitor serializer = factory.create(SPARQLExt.SYNTAX, context, out);
        q.visit(serializer);

        out.decIndent(INDENT);
        out.print("}");
    }

    public void visitAsGroup(Element el) {
        boolean needBraces = !((el instanceof ElementGroup) || (el instanceof ElementSubQuery));

        if (needBraces) {
            out.print("{ ");
            out.incIndent(INDENT);
        }

        el.visit(this);

        if (needBraces) {
            out.decIndent(INDENT);
            out.print("}");
        }
    }

    int subjectWidth = -1;
    int predicateWidth = -1;

    @Override
    protected void formatTriples(BasicPattern triples) {
        if (!PRETTY_PRINT) {
            super.formatTriples(triples);
            return;
        }

        // TODO RDF Collections - spot the parsers pattern
        if (triples.isEmpty()) {
            return;
        }

        setWidths(triples);
        if (subjectWidth > TRIPLES_SUBJECT_COLUMN) {
            subjectWidth = TRIPLES_SUBJECT_COLUMN;
        }
        if (predicateWidth > TRIPLES_PROPERTY_COLUMN) {
            predicateWidth = TRIPLES_PROPERTY_COLUMN;
        }

        // Loops:
        List<Triple> subjAcc = new ArrayList<>(); // Accumulate all triples
        // with the same subject.
        Node subj = null; // Subject being accumulated

        boolean first = true; // Print newlines between blocks.

        int indent = -1;
        for (Triple t : triples) {
            if (subj != null && t.getSubject().equals(subj)) {
                subjAcc.add(t);
                continue;
            }

            if (subj != null) {
                if (!first) {
                    out.println(" .");
                }
                formatSameSubject(subj, subjAcc);
                first = false;
                // At end of line of a block of triples with same subject.
                // Drop through and start new block of same subject triples.
            }

            // New subject
            subj = t.getSubject();
            subjAcc.clear();
            subjAcc.add(t);
        }

        // Flush accumulator
        if (subj != null && subjAcc.size() != 0) {
            if (!first) {
                out.println(" .");
            }
            first = false;
            formatSameSubject(subj, subjAcc);
        }
    }

    private void formatSameSubject(Node subject, List<Triple> triples) {
        if (triples == null || triples.size() == 0) {
            return;
        }

        // Do the first triple.
        Iterator<Triple> iter = triples.iterator();
        Triple t1 = iter.next();

//        int indent = TRIPLES_SUBJECT_COLUMN+TRIPLES_COLUMN_GAP ;
//        // Long subject => same line.  Works for single triple as well.
//        int s1_len = printSubject(t1.getSubject()) ;
//        //int x = out.getCol() ;
        int indent = subjectWidth + TRIPLES_COLUMN_GAP;
        int s1_len = printSubject(t1.getSubject());

        if (s1_len > TRIPLES_SUBJECT_LONG) {
            // Too long - start a new line.
            out.incIndent(indent);
            out.println();
        } else {
            printGap();
            out.incIndent(indent);
        }

        // Remainder of first triple
        printProperty(t1.getPredicate());
        printGap();
        printObject(t1.getObject());

        // Do the rest
        for (; iter.hasNext();) {
            Triple t = iter.next();
            out.println(" ;");
            printProperty(t.getPredicate());
            printGap();
            printObject(t.getObject());
            continue;
            // print property list
        }

        // Finish off the block.
        out.decIndent(indent);
        // out.print(" .") ;
    }

    private void setWidths(BasicPattern triples) {
        subjectWidth = -1;
        predicateWidth = -1;

        for (Triple t : triples) {
            String s = slotToString(t.getSubject());
            if (s.length() > subjectWidth) {
                subjectWidth = s.length();
            }

            String p = slotToString(t.getPredicate());
            if (p.length() > predicateWidth) {
                predicateWidth = p.length();
            }
        }
    }

    private void printGap() {
        out.print(' ', TRIPLES_COLUMN_GAP);
    }

    // Indent must be set first.
    private int printSubject(Node s) {
        String str = slotToString(s);
        out.print(str);
        out.pad(subjectWidth);
        return str.length();
    }

    // Assumes the indent is TRIPLES_SUBJECT_COLUMN+GAP
    private static String RDFTYPE = FmtUtils.stringForNode(RDF.Nodes.type, new SerializationContext());

    private int printProperty(Node p) {
        String str = slotToString(p);
        if (p.equals(RDF.Nodes.type) && str.equals(RDFTYPE)) {
            out.print("a");
        } else {
            out.print(str);
        }
        out.pad(predicateWidth);
        return str.length();
    }

    private int printObject(Node obj) {
        return printNoCol(obj);
    }

    private int printNoCol(Node node) {
        String str = slotToString(node);
        out.print(str);
        return str.length();

    }

    private void visitTexprs(List<Element> elts) {
        for (int i = 0; i < elts.size(); i++) {
            Element e = elts.get(i);
            if (!(e instanceof ElementExpr)) {
                out.newline();
                e.visit(this);
                out.newline();
            } else {
                if (i != 0) {
                    out.print(" ");
                }
                e.visit(this);
            }
        }
    }

}
