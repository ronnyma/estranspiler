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

    private static void parse(final String boolQuery, final String expected) {
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
    void parseQuery1() {
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
                          ))
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
                                  .must(new TermQueryBuilder("oppholdsadresse.matrikkeladresse.bruksenhetsnummer.keyword", bruksenhet)),
                          ScoreMode.Max))
              );
            """;

        final String expected =
            """
                (query (expression (queries (boolQuery boolQuery() . (memberFunctions should) ( (queries (nestedQuery nestedQuery ( "bostedsadresse" , (boolQuery boolQuery() . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.vegadresse.bruksenhetsnummer.keyword" , bruksenhet ))) ))) ) . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword" , bruksenhet ))) ))) )) ))) ) . (memberFunctions should) ( (queries (nestedQuery nestedQuery ( "oppholdsadresse" , (boolQuery boolQuery() . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.vegadresse.bruksenhetsnummer.keyword" , bruksenhet ))) ))) ) . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.matrikkeladresse.bruksenhetsnummer.keyword" , bruksenhet ))) ) , (scoremode ScoreMode.Max))) )) ))) )))) ;)
                """;
        parse(boolQuery, expected);
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
                                 , ScoreMode.Max))
                                 );
            """;

        final String expected =
            """
                (query (expression (queries (boolQuery boolQuery() . (memberFunctions should) ( (queries (nestedQuery nestedQuery ( "bostedsadresse" , (boolQuery boolQuery() . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions should) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.vegadresse.bruksenhetsnummer.keyword" , bruksenhet ))) ))) ) . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions should) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword" , bruksenhet ))) ) , (scoremode ScoreMode.Max))) )) ))) )))) ;)
                """;
        parse(boolQuery, expected);

    }

    @Test
    void parseQuery3() {
        String boolQuery = """
             boolQuery()
                 .must(
                     nestedQuery("identifikasjonsnummer",
                         boolQuery()
                             .must(termQuery("identifikasjonsnummer.type.keyword", "D_NUMMER"))
                             .must(termQuery("identifikasjonsnummer.status.keyword", Identifikatorstatuskode.I_BRUK.name()))
                             .must(termQuery("identifikasjonsnummer.erGjeldende", true))
                             .must(rangeQuery("identifikasjonsnummer.gyldighetstidspunkt").lte("now-5y")),
                         ScoreMode.Max)
                 )
                 .must(
                     nestedQuery("personstatus",
                         boolQuery()
                             .must(termQuery("personstatus.personstatus.keyword", Personstatustype.MIDLERTIDIG.name()))
                             .must(termQuery("personstatus.erGjeldende", true))
                             .must(rangeQuery("personstatus.gyldighetstidspunkt").lte("now-5y")),
                         ScoreMode.Max)
                 )
                 .mustNot(
                     nestedQuery("identitetsgrunnlag",
                         boolQuery()
                             .must(termQuery("identitetsgrunnlag.erGjeldende", true))
                             .must(termQuery("identitetsgrunnlag.legitimasjonskontroll.keyword", Identitetsgrunnlagstatus.KONTROLLERT.name()))
                             .must(rangeQuery("identitetsgrunnlag.ajourholdstidspunkt").gt("now-6M")),
                         ScoreMode.Max)
            
                 );
            """;

        final String expected =
            """
                (query (expression (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (nestedQuery nestedQuery ( "identifikasjonsnummer" , (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery termQuery( "identifikasjonsnummer.type.keyword" , "D_NUMMER" ))) ) . (memberFunctions must) ( (queries (termQuery termQuery( "identifikasjonsnummer.status.keyword" , Identifikatorstatuskode . I_BRUK . name () ))) ) . (memberFunctions must) ( (queries (termQuery termQuery( "identifikasjonsnummer.erGjeldende" , (bool true) ))) ) . (memberFunctions must) ( (queries (rangeQuery rangeQuery( "identifikasjonsnummer.gyldighetstidspunkt" ) . (intervals lte) ( "now-5y" ))) ) , (scoremode ScoreMode.Max)) ))) ) . (memberFunctions must) ( (queries (nestedQuery nestedQuery ( "personstatus" , (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery termQuery( "personstatus.personstatus.keyword" , Personstatustype . MIDLERTIDIG . name () ))) ) . (memberFunctions must) ( (queries (termQuery termQuery( "personstatus.erGjeldende" , (bool true) ))) ) . (memberFunctions must) ( (queries (rangeQuery rangeQuery( "personstatus.gyldighetstidspunkt" ) . (intervals lte) ( "now-5y" ))) ) , (scoremode ScoreMode.Max)) ))) ) . (memberFunctions mustNot) ( (queries (nestedQuery nestedQuery ( "identitetsgrunnlag" , (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery termQuery( "identitetsgrunnlag.erGjeldende" , (bool true) ))) ) . (memberFunctions must) ( (queries (termQuery termQuery( "identitetsgrunnlag.legitimasjonskontroll.keyword" , Identitetsgrunnlagstatus . KONTROLLERT . name () ))) ) . (memberFunctions must) ( (queries (rangeQuery rangeQuery( "identitetsgrunnlag.ajourholdstidspunkt" ) . (intervals gt) ( "now-6M" ))) ) , (scoremode ScoreMode.Max)) ))) )))) ;)
                """;
        parse(boolQuery, expected);

    }@Test
    void parseQuery4() {
        String boolQuery = """
            boolQuery()
                .minimumShouldMatch(1)
                .should(
                    nestedQuery("bostedsadresse",
                        boolQuery()
                            .should(
                                boolQuery()
                                    .must(termQuery("bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                    .must(termQuery("bostedsadresse.erGjeldende", true))
                                    .mustNot(existsQuery("bostedsadresse.vegadresse.bruksenhetsnummer.keyword"))
                                    .mustNot(existsQuery("bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword"))
                            )
                            .should(
                                boolQuery()
                                    .must(new TermQueryBuilder("bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                    .must(new TermQueryBuilder("bostedsadresse.erGjeldende", true))
                                    .must(new TermQueryBuilder("bostedsadresse.vegadresse.bruksenhetsnummer.keyword", ""))
                            )
                            .should(
                                boolQuery()
                                    .must(new TermQueryBuilder("bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                    .must(new TermQueryBuilder("bostedsadresse.erGjeldende", true))
                                    .must(new TermQueryBuilder("bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword", ""))
                            )
                        , ScoreMode.Max)
                )
                .should(
                    nestedQuery("oppholdsadresse",
                        boolQuery()
                            .should(
                                boolQuery()
                                    .must(termQuery("oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                    .must(termQuery("oppholdsadresse.erGjeldende", true))
                                    .mustNot(existsQuery("oppholdsadresse.vegadresse.bruksenhetsnummer.keyword"))
                                    .mustNot(existsQuery("oppholdsadresse.matrikkeladresse.bruksenhetsnummer.keyword"))
                            )
                            .should(
                                boolQuery()
                                    .must(new TermQueryBuilder("oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                    .must(new TermQueryBuilder("oppholdsadresse.erGjeldende", true))
                                    .must(new TermQueryBuilder("oppholdsadresse.vegadresse.bruksenhetsnummer.keyword", ""))
                            )
                            .should(
                                boolQuery()
                                    .must(new TermQueryBuilder("oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword", matrikkelidentifikator))
                                    .must(new TermQueryBuilder("oppholdsadresse.erGjeldende", true))
                                    .must(new TermQueryBuilder("oppholdsadresse.matrikkeladresse.bruksenhetsnummer.keyword", ""))
                            )
                        , ScoreMode.Max)
                );
            """;

        final String expected =
            """
                (query (expression (queries (boolQuery boolQuery() .minimumShouldMatch ( 1 ) . (memberFunctions should) ( (queries (nestedQuery nestedQuery ( "bostedsadresse" , (boolQuery boolQuery() . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery termQuery( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery termQuery( "bostedsadresse.erGjeldende" , (bool true) ))) ) . (memberFunctions mustNot) ( (queries (existsQuery existsQuery( "bostedsadresse.vegadresse.bruksenhetsnummer.keyword" ))) ) . (memberFunctions mustNot) ( (queries (existsQuery existsQuery( "bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword" ))) ))) ) . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.erGjeldende" , (bool true) ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.vegadresse.bruksenhetsnummer.keyword" , "" ))) ))) ) . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.erGjeldende" , (bool true) ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "bostedsadresse.matrikkeladresse.bruksenhetsnummer.keyword" , "" ))) ))) ) , (scoremode ScoreMode.Max)) ))) ) . (memberFunctions should) ( (queries (nestedQuery nestedQuery ( "oppholdsadresse" , (boolQuery boolQuery() . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery termQuery( "oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery termQuery( "oppholdsadresse.erGjeldende" , (bool true) ))) ) . (memberFunctions mustNot) ( (queries (existsQuery existsQuery( "oppholdsadresse.vegadresse.bruksenhetsnummer.keyword" ))) ) . (memberFunctions mustNot) ( (queries (existsQuery existsQuery( "oppholdsadresse.matrikkeladresse.bruksenhetsnummer.keyword" ))) ))) ) . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.erGjeldende" , (bool true) ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.vegadresse.bruksenhetsnummer.keyword" , "" ))) ))) ) . (memberFunctions should) ( (queries (boolQuery boolQuery() . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.adresseIdentifikatorFraMatrikkelen.keyword" , matrikkelidentifikator ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.erGjeldende" , (bool true) ))) ) . (memberFunctions must) ( (queries (termQuery new TermQueryBuilder( "oppholdsadresse.matrikkeladresse.bruksenhetsnummer.keyword" , "" ))) ))) ) , (scoremode ScoreMode.Max)) ))) )))) ;)
                """;
        parse(boolQuery, expected);

    }


}
