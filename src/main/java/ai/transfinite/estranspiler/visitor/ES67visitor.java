package ai.transfinite.estranspiler.visitor;

import ai.transfinite.ES67BaseVisitor;
import ai.transfinite.ES67Parser.BoolClauseContext;
import ai.transfinite.ES67Parser.BoolQueryContext;
import ai.transfinite.ES67Parser.QueryExprContext;
import ai.transfinite.ES67Parser.SubQueryExprContext;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ES67visitor extends ES67BaseVisitor<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ES67visitor.class);

    @Override
    public String visitBoolQuery(final BoolQueryContext ctx) {

        final List<String> head = ctx.boolClause().stream().limit(1L).map(this::visit).toList();
        final List<String> tail = ctx.boolClause().stream().skip(1L).map(this::visit).map(this::normalize).toList();

        final String collect = Stream.of(head, tail).flatMap(Collection::stream).collect(Collectors.joining());

        LOGGER.info("BoolQuery.of({})", collect);

        return collect;

    }

    private String normalize(final String string) {
        return StringUtils.replaceAll(string, "b -> b", "\n");
    }

    @Override
    public String visitBoolClause(final BoolClauseContext ctx) {
        if (ctx.MUST() != null) {
            final String s = visit(ctx.subQueryExpr());
            return "b -> b.must(" + s + ")";
        } else if (ctx.SHOULD() != null) {
            final String s = visit(ctx.subQueryExpr());
            return "b -> b.should(" + s + ")";
        } else if (ctx.MUSTNOT() != null) {
            final String s = visit(ctx.subQueryExpr());
            return "b -> b.mustNot(" + s + ")";
        }
        return "";
    }

    @Override
    public String visitSubQueryExpr(final SubQueryExprContext ctx) {
        if (ctx.queryExpr().MATCHQUERY() != null) {
            final String s = visit(ctx.queryExpr());
            return "s -> s.match(" + s + ")";
        } else if (ctx.queryExpr().TERMQUERY() != null) {
            final String s = visit(ctx.queryExpr());
            return "s -> s.term(" + s + ")";
        }
        return "";
    }

    @Override
    public String visitQueryExpr(final QueryExprContext ctx) {
        if (ctx.MATCHQUERY() != null) {
            String field = stripQuotes(ctx.variable(0).getText());
            String value = ctx.variable(1).getText();

            return "f -> f.field(\"" + field + "\").query(" + value + ")";

        } else if (ctx.TERMQUERY() != null) {
            String field = stripQuotes(ctx.variable(0).getText());
            String value = ctx.variable(1).getText();

            return "f -> f.field(\"" + field + "\").value(" + value + ")";
        }
        return "";
    }


    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
