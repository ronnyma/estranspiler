package ai.transfinite.estranspiler.visitor;


import static org.assertj.core.api.Assertions.assertThat;

import ai.transfinite.ES627Lexer;
import ai.transfinite.ES627Parser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

public class ES627VisitorTest {

    @Test
    void visitAssign() {
        String boolQuery = """
             boolQuery()
              .should(
                  nestedQuery("bostedsadresse",
                      boolQuery()
                          .should(
                              boolQuery()
                                  .must(new TermQueryBuilder("bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                  .must(new TermQueryBuilder("bostedsadresse.vegadresse.bruksenhetsnummer.keyword", bruksenhet))
                          )
                          .should(
                              boolQuery()
                                  .must(new TermQueryBuilder("bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                  .must(new TermQueryBuilder("bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword", bruksenhet))
                          )
                      , ScoreMode.Max)
              )
              .should(
                  nestedQuery("oppholdsadresse",
                      boolQuery()
                          .should(
                              boolQuery()
                                  .must(new TermQueryBuilder("oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                  .must(new TermQueryBuilder("oppholdsadresse.vegadresse.bruksenhetsnummer.keyword", bruksenhet))
                          )
                          .should(
                              boolQuery()
                                  .must(new TermQueryBuilder("oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                  .must(new TermQueryBuilder("oppholdsadresse.matrikkeladresse.bruksenhetsnummer.keyword", bruksenhet))
                          )
                      , ScoreMode.Max)
              );
            """;

        final String expected =
            """
                (query (expression (queries (boolQuery boolQuery() . should ( (nestedQuery nestedQuery ( "bostedsadresse" , (boolQuery boolQuery() . should ( (boolQuery boolQuery() . must ( (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" ,  matrikkelidentifikator )) ) . must ( (termQuery new TermQueryBuilder( "bostedsadresse.vegadresse.bruksenhetsnummer.keyword" ,  bruksenhet )) )) ) . should ( (boolQuery boolQuery() . must ( (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" ,  matrikkelidentifikator )) ) . must ( (termQuery new TermQueryBuilder( "bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword" ,  bruksenhet )) )) )) ,  ScoreMode.Max) )))) ) . should ( nestedQuery ( "oppholdsadresse" , boolQuery() . should ( boolQuery() . must ( new TermQueryBuilder( "oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword" ,  matrikkelidentifikator ) ) . must ( new TermQueryBuilder( "oppholdsadresse.vegadresse.bruksenhetsnummer.keyword" ,  bruksenhet ) ) ) . should ( boolQuery() . must ( new TermQueryBuilder( "oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword" ,  matrikkelidentifikator ) ) . must ( new TermQueryBuilder( "oppholdsadresse.matrikkeladresse.bruksenhetsnummer.keyword" ,  bruksenhet ) ) ) ,  ScoreMode.Max ) ) ;)
                """;
        CharStream input = CharStreams.fromString(boolQuery);
        ES627Lexer lexer = new ES627Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ES627Parser parser = new ES627Parser(tokens);
        ParseTree tree = parser.query();
        ES627Visitor eval = new ES627Visitor();
        eval.visit(tree);
        final String stringTree = tree.toStringTree(parser);
        assertThat(stringTree).isEqualToIgnoringWhitespace(expected);
    }

    @Test
    void parseQuery2() {
        String boolQuery = """
              boolQuery()
                     .should(
                         nestedQuery("bostedsadresse",
                             boolQuery()
                                 .should(
                                     boolQuery()
                                         .must(new TermQueryBuilder("bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                         .should(new TermQueryBuilder("bostedsadresse.vegadresse.bruksenhetsnummer.keyword", bruksenhet))
                                 )
                                 .should(
                                     boolQuery()
                                         .should(new TermQueryBuilder("bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                         .must(new TermQueryBuilder("bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword", bruksenhet))
                                 )));
            """;

        final String expected =
            """
                (query (expression (queries (boolQuery boolQuery() . should ( (nestedQuery nestedQuery ( "bostedsadresse" , (boolQuery boolQuery() . should ( (boolQuery boolQuery() . must ( (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" ,  matrikkelidentifikator )) ) . should ( (termQuery new TermQueryBuilder( "bostedsadresse.vegadresse.bruksenhetsnummer.keyword" ,  bruksenhet )) )) ) . should ( (boolQuery boolQuery() . should ( (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" ,  matrikkelidentifikator )) ) . must ( (termQuery new TermQueryBuilder( "bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword" ,  bruksenhet )) )) )) )) )))) ;)
                """;
        CharStream input = CharStreams.fromString(boolQuery);
        ES627Lexer lexer = new ES627Lexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ES627Parser parser = new ES627Parser(tokens);
        ParseTree tree = parser.query();
        ES627Visitor eval = new ES627Visitor();
        eval.visit(tree);
        final String stringTree = tree.toStringTree(parser);
        assertThat(stringTree).isEqualToIgnoringWhitespace(expected);

    }
}
