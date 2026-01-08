package ai.transfinite.estranspiler.visitor;

import ai.transfinite.RegisterDSLBaseVisitor;
import ai.transfinite.RegisterDSLParser.BooleanValueContext;
import ai.transfinite.RegisterDSLParser.DslArrayContext;
import ai.transfinite.RegisterDSLParser.DslContext;
import ai.transfinite.RegisterDSLParser.FieldContext;
import ai.transfinite.RegisterDSLParser.FieldsContext;
import ai.transfinite.RegisterDSLParser.MultipleDslContext;
import ai.transfinite.RegisterDSLParser.NumberValueContext;
import ai.transfinite.RegisterDSLParser.SingleDslContext;
import ai.transfinite.RegisterDSLParser.StringValueContext;
import ai.transfinite.RegisterDSLParser.ValueContext;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visitor implementation for transforming Register DSL to Elasticsearch queries.
 * 
 * <p><b>How the Visitor Pattern Works:</b></p>
 * <p>The Visitor pattern provides <i>explicit, pull-based</i> control over tree traversal.
 * Unlike the Listener pattern which automatically walks the entire tree, the Visitor allows
 * you to decide <i>when</i> and <i>which</i> child nodes to visit, and can return values from
 * visit methods.</p>
 * 
 * <p><b>Key Concepts:</b></p>
 * <ul>
 *   <li><b>Pull-based:</b> You explicitly call {@code visit()} on child nodes you want to traverse</li>
 *   <li><b>Return values:</b> Each visit method returns a {@code Query} object (the generic type parameter)</li>
 *   <li><b>Control flow:</b> You control the traversal order and can skip nodes if needed</li>
 *   <li><b>No automatic walking:</b> Unlike Listener, nothing happens unless you call visit methods</li>
 * </ul>
 * 
 * <p><b>Visitor Workflow:</b></p>
 * <pre>
 * 1. Parser creates parse tree from input: root -> singleDsl -> dsl -> fields -> field...
 * 2. You call visitor.visit(tree)
 * 3. Visitor dispatches to appropriate visit method based on node type (e.g., visitDsl)
 * 4. In visit method, you:
 *    - Extract data from context (ctx)
 *    - Explicitly visit child nodes by calling visit(ctx.childNode())
 *    - Build and return a Query object
 * 5. Return value bubbles up to caller
 * </pre>
 * 
 * <p><b>Example Flow for Single DSL:</b></p>
 * <pre>
 * Input: {"entity": "sivilstand.sivilstand", "operator": "har", "verdi": "gift", "ergjeldende": true}
 * 
 * visitRoot()
 *   ↓
 * visitSingleDsl() - dispatches to visitDsl()
 *   ↓
 * visitDsl()
 *   - Calls visitFields() to populate fieldMap
 *   - visitFields() iterates and calls visitField() for each field
 *   - visitField() extracts field name and value into fieldMap
 *   - Back in visitDsl(), builds term queries from fieldMap
 *   - Wraps in BoolQuery (must or must_not based on operator)
 *   - Wraps in NestedQuery with path
 *   - Returns final Query object
 * </pre>
 * 
 * <p><b>Array Handling:</b></p>
 * <p>For arrays like {@code [{...}, {...}]}, the flow is:
 * <pre>
 * visitMultipleDsl()
 *   ↓
 * visitDslArray()
 *   - Iterates over each DSL in array
 *   - For each: calls buildDslBoolQuery(dsl) which internally visits fields
 *   - Collects all bool queries into a list
 *   - Wraps all in outer BoolQuery with must
 *   - Wraps in NestedQuery
 *   - Returns combined Query
 * </pre>
 * 
 * <p><b>Key Methods:</b></p>
 * <ul>
 *   <li>{@link #visitSingleDsl} - Entry point for single DSL object</li>
 *   <li>{@link #visitMultipleDsl} - Entry point for DSL array</li>
 *   <li>{@link #visitDsl} - Main logic for transforming single DSL to Query</li>
 *   <li>{@link #visitFields} - Populates fieldMap by visiting each field</li>
 *   <li>{@link #visitField} - Extracts field name and value into fieldMap</li>
 * </ul>
 * 
 * <p><b>Why Use Visitor Here?</b></p>
 * <ul>
 *   <li>We need to return Query objects - Visitor supports return values naturally</li>
 *   <li>We want control over traversal - only visit fields we need</li>
 *   <li>Bottom-up construction - build Query from leaf nodes upward</li>
 *   <li>Simpler state management - less tracking needed than Listener</li>
 * </ul>
 * 
 * @see RegisterDSLListener for the alternative Listener (event-driven) implementation
 */
public class RegisterDSLVisitor extends RegisterDSLBaseVisitor<Query> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterDSLVisitor.class);

    /**
     * Temporary storage for field-value pairs from the current DSL object being processed.
     * <p>This map is populated by {@link #visitFields} and cleared before each new DSL.
     * For example, after parsing {"entity": "sivilstand.sivilstand", "verdi": "gift"},
     * the map will contain: {"entity" -> "sivilstand.sivilstand", "verdi" -> "gift"}</p>
     */
    private final Map<String, String> fieldMap = new HashMap<>();

    /**
     * Visits a single DSL object (not an array).
     * <p>This is called when the input is: {@code {"entity": "...", ...}}</p>
     * <p><b>Visitor Control:</b> We explicitly delegate to visitDsl() which does the real work.
     * This is the "pull" aspect of Visitor - we decide to visit the child dsl node.</p>
     * 
     * @param ctx the parse tree node for a single DSL object
     * @return a complete Elasticsearch Query object
     */
    @Override
    public Query visitSingleDsl(SingleDslContext ctx) {
        // Explicit visitor control: we choose to visit the child dsl node
        // This returns a Query object that we pass up to our caller
        return visitDsl(ctx.dsl());
    }

    /**
     * Visits an array of DSL objects.
     * <p>This is called when the input is: {@code [{"entity": "..."}, {"entity": "..."}]}</p>
     * <p><b>Visitor Control:</b> We explicitly delegate to visitDslArray().</p>
     * 
     * @param ctx the parse tree node for multiple DSL objects
     * @return a complete Elasticsearch Query with nested bool structure
     */
    @Override
    public Query visitMultipleDsl(MultipleDslContext ctx) {
        // Explicit visitor control: delegate to the array handler
        return visitDslArray(ctx.dslArray());
    }

    /**
     * Processes an array of DSL objects and builds a combined Elasticsearch query.
     * <p><b>Visitor Control Flow:</b></p>
     * <ol>
     *   <li>Extract all DSL contexts from the array (no visiting yet)</li>
     *   <li>For each DSL, call buildDslBoolQuery() which internally visits fields</li>
     *   <li>Combine all individual bool queries into one outer bool query</li>
     *   <li>Wrap in a single nested query</li>
     * </ol>
     * 
     * <p><b>Example:</b></p>
     * <pre>
     * Input: [{"entity": "sivilstand.sivilstand", "operator": "har", "verdi": "gift"}, 
     *         {"entity": "sivilstand.sivilstand", "operator": "harIkke", "verdi": "ugift"}]
     * Output: NestedQuery(path="document.sivilstand",
     *           query=BoolQuery(must=[
     *             BoolQuery(must=[term(...)]),
     *             BoolQuery(must_not=[term(...)])
     *           ]))
     * </pre>
     * 
     * @param ctx the parse tree node containing the array of DSL objects
     * @return a complete Query combining all DSL objects
     */
    @Override
    public Query visitDslArray(DslArrayContext ctx) {
        // Step 1: Get all DSL contexts from the array (direct access, no visiting)
        List<DslContext> dslList = ctx.dsl();
        
        if (dslList.isEmpty()) {
            throw new IllegalArgumentException("DSL array cannot be empty");
        }

        // Step 2: Extract base path from first DSL (all DSLs in array must have same path)
        // This internally visits the fields of the first DSL
        String basePath = extractBasePath(dslList.get(0));

        // Step 3: Build a bool query for each DSL in the array
        // Each call to buildDslBoolQuery() internally visits that DSL's fields
        List<Query> dslQueries = new ArrayList<>();
        for (DslContext dsl : dslList) {
            Query dslBoolQuery = buildDslBoolQuery(dsl);
            dslQueries.add(dslBoolQuery);
        }

        // Step 4: Wrap all individual bool queries in an outer bool query with must
        // This creates: bool { must: [<query1>, <query2>, ...] }
        BoolQuery outerBoolQuery = BoolQuery.of(b -> b.must(dslQueries));

        // Step 5: Wrap the outer bool query in a nested query
        // This creates: nested { path: "document.sivilstand", query: <outerBoolQuery> }
        NestedQuery nestedQuery = NestedQuery.of(n -> n
            .path("document." + basePath)
            .query(q -> q.bool(outerBoolQuery))
        );

        // Step 6: Create the final Query object and return
        Query result = Query.of(q -> q.nested(nestedQuery));
        LOGGER.info("Generated query: {}", result);
        return result;
    }

    private String extractBasePath(DslContext ctx) {
        fieldMap.clear();
        visitFields(ctx.fields());
        String entity = stripQuotes(fieldMap.get("entity"));
        if (entity == null) {
            throw new IllegalArgumentException("Missing required field: entity");
        }
        String[] parts = entity.split("\\.", 2);
        return parts[0];
    }

    private Query buildDslBoolQuery(DslContext ctx) {
        fieldMap.clear();
        visitFields(ctx.fields());

        String entity = stripQuotes(fieldMap.get("entity"));
        String operator = stripQuotes(fieldMap.get("operator"));
        String verdi = fieldMap.get("verdi");
        String ergjeldende = fieldMap.get("ergjeldende");

        if (entity == null || verdi == null) {
            throw new IllegalArgumentException("Missing required fields: entity or verdi");
        }
        
        // Determine which clause type to use based on operator
        boolean isNegation = "harIkke".equals(operator);

        String[] parts = entity.split("\\.", 2);
        String basePath = parts[0];
        String fullField = entity;

        // Build list of term queries
        List<Query> termQueries = new ArrayList<>();
        termQueries.add(buildTermQuery("document." + fullField, verdi));
        
        if (ergjeldende != null) {
            termQueries.add(buildTermQuery("document." + basePath + ".ergjeldende", ergjeldende));
        }

        // Build bool query with must or must_not
        BoolQuery boolQuery;
        if (isNegation) {
            boolQuery = BoolQuery.of(b -> b.mustNot(termQueries));
        } else {
            boolQuery = BoolQuery.of(b -> b.must(termQueries));
        }

        return Query.of(q -> q.bool(boolQuery));
    }

    /**
     * Main transformation method - converts a single DSL object to an Elasticsearch Query.
     * <p><b>This is the heart of the Visitor implementation.</b></p>
     * 
     * <p><b>Visitor Pattern in Action:</b></p>
     * <ol>
     *   <li><b>Explicit child visitation:</b> We call visitFields(ctx.fields()) to traverse children</li>
     *   <li><b>Data extraction:</b> Child visits populate fieldMap which we then use</li>
     *   <li><b>Return value:</b> We build and return a Query object (not possible with Listener)</li>
     *   <li><b>Control:</b> We decide when and how to visit children, and can skip nodes</li>
     * </ol>
     * 
     * <p><b>Transformation Steps:</b></p>
     * <pre>
     * Input:  {"entity": "sivilstand.sivilstand", "operator": "har", "verdi": "gift", "ergjeldende": true}
     * 
     * Step 1: Visit fields -> fieldMap = {entity: "sivilstand.sivilstand", verdi: "gift", ...}
     * Step 2: Parse entity -> basePath = "sivilstand", fullField = "sivilstand.sivilstand"
     * Step 3: Build term queries:
     *         - term("document.sivilstand.sivilstand", "gift")
     *         - term("document.sivilstand.ergjeldende", true)
     * Step 4: Wrap in bool query -> bool(must=[...term queries...])
     * Step 5: Wrap in nested query -> nested(path="document.sivilstand", query=boolQuery)
     * Step 6: Return the complete Query object
     * </pre>
     * 
     * @param ctx the parse tree node for a single DSL object
     * @return a complete Elasticsearch nested Query
     */
    @Override
    public Query visitDsl(DslContext ctx) {
        // Step 1: Clear state for this DSL object
        // (Important because fieldMap is reused across multiple DSL objects)
        fieldMap.clear();

        // Step 2: VISITOR CONTROL - Explicitly visit the fields child node
        // This is the "pull" aspect: we decide to traverse into the fields
        // visitFields() will iterate over field nodes and populate fieldMap
        visitFields(ctx.fields());

        // Step 3: Extract values from the populated fieldMap
        // After visiting fields, fieldMap contains: {"entity" -> "...", "verdi" -> "...", etc.}
        String entity = stripQuotes(fieldMap.get("entity"));
        String operator = stripQuotes(fieldMap.get("operator"));
        String verdi = fieldMap.get("verdi");
        String ergjeldende = fieldMap.get("ergjeldende");

        // Validate required fields
        if (entity == null || verdi == null) {
            throw new IllegalArgumentException("Missing required fields: entity or verdi");
        }
        
        // Step 4: Determine query type based on operator
        // "har" -> must (include), "harIkke" -> must_not (exclude)
        boolean isNegation = "harIkke".equals(operator);

        // Step 5: Parse entity to extract base path and full field
        // Example: "sivilstand.sivilstand" -> basePath="sivilstand", fullField="sivilstand.sivilstand"
        String[] parts = entity.split("\\.", 2);
        String basePath = parts[0];
        String fullField = entity;

        // Step 6: Build list of term queries
        List<Query> termQueries = new ArrayList<>();
        
        // First term query: matches the main entity field
        // Creates: {"term": {"document.sivilstand.sivilstand": {"value": "gift"}}}
        termQueries.add(buildTermQuery("document." + fullField, verdi));
        
        // Second term query: matches ergjeldende if present
        // Creates: {"term": {"document.sivilstand.ergjeldende": {"value": true}}}
        if (ergjeldende != null) {
            termQueries.add(buildTermQuery("document." + basePath + ".ergjeldende", ergjeldende));
        }

        // Step 7: Wrap term queries in a bool query (must or must_not)
        // Using Elasticsearch functional API: BoolQuery.of(builder -> builder.must(...))
        BoolQuery boolQuery;
        if (isNegation) {
            // Exclude: {"bool": {"must_not": [<term queries>]}}
            boolQuery = BoolQuery.of(b -> b.mustNot(termQueries));
        } else {
            // Include: {"bool": {"must": [<term queries>]}}
            boolQuery = BoolQuery.of(b -> b.must(termQueries));
        }

        // Step 8: Wrap bool query in a nested query
        // Nested queries are required for searching within nested documents
        // Creates: {"nested": {"path": "document.sivilstand", "query": <boolQuery>}}
        NestedQuery nestedQuery = NestedQuery.of(n -> n
            .path("document." + basePath)
            .query(q -> q.bool(boolQuery))
        );

        // Step 9: Create final Query object and return
        // This is the key Visitor benefit: we return a value that bubbles up the call chain
        Query result = Query.of(q -> q.nested(nestedQuery));
        LOGGER.info("Generated query: {}", result);
        return result;
    }

    private Query buildTermQuery(String field, String value) {
        // Remove quotes from value if it's a string
        String cleanValue = stripQuotes(value);
        
        // Try to parse as boolean
        if ("true".equals(value) || "false".equals(value)) {
            boolean boolValue = Boolean.parseBoolean(value);
            return Query.of(q -> q.term(t -> t.field(field).value(boolValue)));
        }
        
        // Try to parse as number
        try {
            if (value.contains(".")) {
                double doubleValue = Double.parseDouble(value);
                return Query.of(q -> q.term(t -> t.field(field).value(doubleValue)));
            } else {
                long longValue = Long.parseLong(value);
                return Query.of(q -> q.term(t -> t.field(field).value(longValue)));
            }
        } catch (NumberFormatException e) {
            // Not a number, treat as string
        }
        
        // Default to string value (with quotes preserved for JSON strings)
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return Query.of(q -> q.term(t -> t.field(field).value(cleanValue)));
        }
        
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    /**
     * Visits all fields in a DSL object and populates the fieldMap.
     * <p><b>Visitor Pattern:</b> We explicitly iterate and visit each child field.
     * This is another example of pull-based control - we decide to visit all fields.</p>
     * 
     * <p>For input: {@code {"entity": "sivilstand", "verdi": "gift"}}<br>
     * This method visits each field node, which populates fieldMap with:<br>
     * {@code {"entity" -> "sivilstand", "verdi" -> "gift"}}</p>
     * 
     * @param ctx the parse tree node containing all fields
     * @return null (this method is called for side effects, not for its return value)
     */
    @Override
    public Query visitFields(FieldsContext ctx) {
        // VISITOR CONTROL: Explicitly iterate over all field children
        // We decide to visit each one by calling visitField()
        // This populates fieldMap as a side effect
        for (FieldContext field : ctx.field()) {
            visitField(field);
        }
        // Return null because we're only interested in the side effect (populating fieldMap)
        // The Visitor pattern allows null returns when we don't need to bubble up a value
        return null;
    }

    /**
     * Visits a single field and extracts its name and value into fieldMap.
     * <p><b>Visitor Pattern:</b> We call visitValue() to explicitly get the value from the child node.</p>
     * 
     * <p>For input: {@code "entity": "sivilstand.sivilstand"}<br>
     * This extracts: fieldName="entity", value="sivilstand.sivilstand"<br>
     * And stores: fieldMap.put("entity", "sivilstand.sivilstand")</p>
     * 
     * @param ctx the parse tree node for a single field (name: value pair)
     * @return null (this method is called for side effects)
     */
    @Override
    public Query visitField(FieldContext ctx) {
        // Extract field name from the STRING token (left side of ":")
        // Example: for "entity": "...", this gets "entity"
        String fieldName = stripQuotes(ctx.STRING().getText());
        
        // VISITOR CONTROL: Explicitly visit the value node to get its value
        // This is pull-based: we decide to visit the value child
        String value = visitValue(ctx.value());
        
        // Store the field name and value in our map for later use
        // This is the side effect that visitDsl() depends on
        fieldMap.put(fieldName, value);
        
        // Return null - we're only interested in the side effect
        return null;
    }

    private String visitValue(ValueContext ctx) {
        if (ctx instanceof StringValueContext) {
            return ((StringValueContext) ctx).STRING().getText();
        } else if (ctx instanceof BooleanValueContext) {
            return ((BooleanValueContext) ctx).BOOLEAN().getText();
        } else if (ctx instanceof NumberValueContext) {
            return ((NumberValueContext) ctx).NUMBER().getText();
        }
        return null;
    }

    private String stripQuotes(String s) {
        if (s == null) {
            return null;
        }
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
