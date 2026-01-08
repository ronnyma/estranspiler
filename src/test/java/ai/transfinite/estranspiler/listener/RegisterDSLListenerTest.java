package ai.transfinite.estranspiler.listener;

import ai.transfinite.RegisterDSLLexer;
import ai.transfinite.RegisterDSLParser;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegisterDSLListenerTest {

    private Query parseQuery(final String input) {
        CharStream charStream = CharStreams.fromString(input);
        RegisterDSLLexer lexer = new RegisterDSLLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RegisterDSLParser parser = new RegisterDSLParser(tokens);
        ParseTree tree = parser.root();
        
        // Use ParseTreeWalker with Listener
        RegisterDSLListener listener = new RegisterDSLListener();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, tree);
        
        return listener.getResult();
    }

    private String parse(final String input) {
        Query query = parseQuery(input);
        // Remove "Query: " prefix from toString() and wrap in {"query": ...}
        String queryJson = query.toString();
        if (queryJson.startsWith("Query: ")) {
            queryJson = queryJson.substring(7); // Remove "Query: " prefix
        }
        return "{\n  \"query\": " + queryJson + "\n}";
    }

    @Test
    void testSivilstandTransformation() {
        String input = """
            {
              "entity" : "sivilstand.sivilstand",
              "operator" : "har",
              "verdi" : "gift",
              "ergjeldende" : true
            }
            """;

        String result = parse(input);
        assertNotNull(result);

        // Verify key components of the output
        assertTrue(result.contains("\"nested\""), "Should contain nested query");
        assertTrue(result.contains("\"path\":\"document.sivilstand\""), "Should have correct nested path");
        assertTrue(result.contains("\"bool\""), "Should contain bool query");
        assertTrue(result.contains("\"must\""), "Should contain must clause");
        assertTrue(result.contains("\"document.sivilstand.sivilstand\":{\"value\":\"gift\"}"), "Should contain term query for entity");
        assertTrue(result.contains("\"document.sivilstand.ergjeldende\":{\"value\":true}"), "Should contain term query for ergjeldende");
    }

    @Test
    void testWithoutErgjeldende() {
        String input = """
            {
              "entity" : "navn.fornavn",
              "operator" : "har",
              "verdi" : "Ole"
            }
            """;

        String result = parse(input);
        assertNotNull(result);

        // Verify key components
        assertTrue(result.contains("\"path\":\"document.navn\""), "Should have correct nested path");
        assertTrue(result.contains("\"document.navn.fornavn\":{\"value\":\"Ole\"}"), "Should contain term query for entity");
        assertFalse(result.contains("ergjeldende"), "Should not contain ergjeldende field");
    }

    @Test
    void testHarIkkeOperator() {
        String input = """
            {
              "entity" : "sivilstand.sivilstand",
              "operator" : "harIkke",
              "verdi" : "gift",
              "ergjeldende" : true
            }
            """;

        String result = parse(input);
        assertNotNull(result);

        // Verify it uses must_not instead of must
        assertTrue(result.contains("\"must_not\""), "Should contain must_not clause for harIkke operator");
        assertTrue(result.contains("\"document.sivilstand.sivilstand\":{\"value\":\"gift\"}"), "Should contain term query for entity");
        assertTrue(result.contains("\"document.sivilstand.ergjeldende\":{\"value\":true}"), "Should contain term query for ergjeldende");
    }

    @Test
    void testArrayOfDslObjects() {
        String input = """
            [
            {
              "entity" : "sivilstand.sivilstand",
              "operator" : "har",
              "verdi" : "gift",
              "ergjeldende" : true
            },
            {
              "entity" : "sivilstand.sivilstand",
              "operator" : "har",
              "verdi" : "ugift",
              "ergjeldende" : false
            }]
            """;

        String result = parse(input);
        assertNotNull(result);

        // Verify structure
        assertTrue(result.contains("\"nested\""), "Should contain nested query");
        assertTrue(result.contains("\"path\":\"document.sivilstand\""), "Should have correct nested path");
        assertTrue(result.contains("\"bool\""), "Should contain bool query");
        assertTrue(result.contains("\"must\""), "Should contain must clauses");
        
        // Verify first DSL object
        assertTrue(result.contains("\"document.sivilstand.sivilstand\":{\"value\":\"gift\"}"), "Should contain first term query");
        assertTrue(result.contains("\"document.sivilstand.ergjeldende\":{\"value\":true}"), "Should contain first ergjeldende query");
        
        // Verify second DSL object
        assertTrue(result.contains("\"document.sivilstand.sivilstand\":{\"value\":\"ugift\"}"), "Should contain second term query");
        assertTrue(result.contains("\"document.sivilstand.ergjeldende\":{\"value\":false}"), "Should contain second ergjeldende query");
    }

    @Test
    void testArrayWithMixedOperators() {
        String input = """
            [
            {
              "entity" : "sivilstand.sivilstand",
              "operator" : "har",
              "verdi" : "gift",
              "ergjeldende" : true
            },
            {
              "entity" : "sivilstand.sivilstand",
              "operator" : "harIkke",
              "verdi" : "ugift",
              "ergjeldende" : false
            }]
            """;

        String result = parse(input);
        assertNotNull(result);

        // Verify structure contains both must and must_not
        assertTrue(result.contains("\"must\""), "Should contain must clause");
        assertTrue(result.contains("\"must_not\""), "Should contain must_not clause");
        assertTrue(result.contains("\"document.sivilstand.sivilstand\":{\"value\":\"gift\"}"), "Should contain first term query");
        assertTrue(result.contains("\"document.sivilstand.sivilstand\":{\"value\":\"ugift\"}"), "Should contain second term query");
    }

    @Test
    void testMissingEntityField() {
        String input = """
            {
              "operator" : "har",
              "verdi" : "gift"
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> parse(input), 
            "Should throw exception when entity field is missing");
    }

    @Test
    void testMissingVerdiField() {
        String input = """
            {
              "entity" : "sivilstand.sivilstand",
              "operator" : "har"
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> parse(input), 
            "Should throw exception when verdi field is missing");
    }
}
