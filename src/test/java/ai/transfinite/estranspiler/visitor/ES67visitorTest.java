package ai.transfinite.estranspiler.visitor;

import ai.transfinite.ES67Lexer;
import ai.transfinite.ES67Parser;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
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


    @Test
    void parseQuery3() {
        String boolQuery = """
                boolQuery()
                 .must(termQuery("identifikasjonsnummer.type.keyword", "D_NUMMER"))
                 .must(termQuery("identifikasjonsnummer.status.keyword", variabel))
                 .must(new TermQueryBuilder("identifikasjonsnummer.erGjeldende", true))
                 .should(termQuery("identifikasjonsnummer.erGjeldende", true))
                 .should(matchQuery("identifikasjonsnummer.erGjeldende", true))
            """;
        parse(boolQuery);

    }
}