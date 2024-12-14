package ai.transfinite.estranspiler.visitor;

import ai.transfinite.ES67Lexer;
import ai.transfinite.ES67Parser;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ScoreMode;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

class ES67visitorTest {

    private static void parse(final String boolQuery) {
        CharStream input = CharStreams.fromString(boolQuery);
        ES67Lexer lexer = new ES67Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ES67Parser parser = new ES67Parser(tokens);
        ParseTree tree = parser.query();
        ES67visitor eval = new ES67visitor();
        eval.visit(tree);
    }

    void example() {
        BoolQuery.of(b -> b.must(s -> s.term(f -> f.field("identifikasjonsnummer.type.keyword").value("D_NUMMER")))
            .must(s -> s.term(f -> f.field("identifikasjonsnummer.status.keyword").value("variabel")))
            .must(s -> s.term(f -> f.field("identifikasjonsnummer.erGjeldende").value(true)))
            .should(s -> s.term(f -> f.field("identifikasjonsnummer.erGjeldende").value(true)))
            .should(s -> s.match(f -> f.field("identifikasjonsnummer.erGjeldende").query(true))));

    }

    @Test
    void parseQuery3() {
        String boolQuery = """
                boolQuery()
                 .must(termQuery("identifikasjonsnummer.type.keyword", "D_NUMMER"))
                 .must(termQuery("identifikasjonsnummer.status.keyword", variabel))
                 .must(new TermQueryBuilder("identifikasjonsnummer.erGjeldende", true))
                 .should(termQuery("identifikasjonsnummer.erGjeldende", true))
                 .mustNot(termQuery("identifikasjonsnummer.erGjeldende", true))
                 .should(matchQuery("identifikasjonsnummer.erGjeldende", true)))
            """;
        parse(boolQuery);

    }
}