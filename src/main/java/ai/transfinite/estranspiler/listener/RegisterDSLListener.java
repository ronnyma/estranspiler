package ai.transfinite.estranspiler.listener;

import ai.transfinite.RegisterDSLBaseListener;
import ai.transfinite.RegisterDSLParser.*;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Listener implementation for transforming Register DSL to Elasticsearch queries.
 * 
 * <p><b>How the Listener Pattern Works:</b></p>
 * <p>The Listener pattern provides <i>automatic, push-based</i> event-driven tree traversal.
 * Unlike the Visitor pattern where you explicitly call visit() on child nodes, the Listener
 * uses a {@link org.antlr.v4.runtime.tree.ParseTreeWalker} that automatically walks the entire
 * tree and fires events when entering/exiting each node.</p>
 * 
 * <p><b>Key Concepts:</b></p>
 * <ul>
 *   <li><b>Push-based:</b> Walker automatically calls your enter/exit methods as it traverses</li>
 *   <li><b>Event-driven:</b> React to enterX() and exitX() events for each rule</li>
 *   <li><b>No return values:</b> All methods are void - use instance variables for state</li>
 *   <li><b>Automatic walking:</b> You don't control traversal - walker handles everything</li>
 * </ul>
 * 
 * <p><b>Listener Workflow:</b></p>
 * <pre>
 * 1. Parser creates parse tree from input
 * 2. You create a Listener and ParseTreeWalker
 * 3. Call walker.walk(listener, tree)
 * 4. Walker does depth-first traversal automatically:
 *    - Before visiting node: calls enterX(ctx)
 *    - Recursively visits all children (automatically)
 *    - After visiting children: calls exitX(ctx)
 * 5. You accumulate results in instance variables
 * 6. After walk completes, retrieve result via getResult()
 * </pre>
 * 
 * <p><b>Example Flow for Single DSL:</b></p>
 * <pre>
 * Input: {"entity": "sivilstand.sivilstand", "operator": "har", "verdi": "gift", "ergjeldende": true}
 * 
 * walker.walk(listener, tree)
 *   ↓
 * enterRoot()
 *   ↓
 * enterSingleDsl() - sets isArrayMode = false
 *   ↓
 * enterDsl() - creates new fieldMap, pushes to stack
 *   ↓
 * enterFields() - (no action needed)
 *   ↓
 * enterField() - (called for each field)
 * exitField()  - extracts name/value into currentFieldMap
 *   ↓
 * exitFields() - (no action needed)
 *   ↓
 * exitDsl() - pops fieldMap, builds Query, pushes to queryStack
 *   ↓
 * exitSingleDsl() - (no action needed)
 *   ↓
 * exitRoot() - (no action needed)
 * 
 * listener.getResult() - returns Query from queryStack
 * </pre>
 * 
 * <p><b>State Management:</b></p>
 * <p>Because Listener methods can't return values, we use instance variables:
 * <ul>
 *   <li>{@link #queryStack} - Accumulates built Query objects</li>
 *   <li>{@link #fieldMapStack} - Tracks field maps for nested DSL objects</li>
 *   <li>{@link #currentFieldMap} - Current DSL's field-value pairs</li>
 *   <li>{@link #dslList} - Collects DSL field maps when processing arrays</li>
 *   <li>{@link #isArrayMode} - Flag to track single vs array mode</li>
 * </ul>
 * 
 * <p><b>Key Differences from Visitor:</b></p>
 * <table border="1">
 *   <tr><th>Aspect</th><th>Visitor</th><th>Listener</th></tr>
 *   <tr><td>Traversal</td><td>Manual (you call visit)</td><td>Automatic (walker handles it)</td></tr>
 *   <tr><td>Control</td><td>Pull-based</td><td>Push-based</td></tr>
 *   <tr><td>Return values</td><td>Yes</td><td>No (use state)</td></tr>
 *   <tr><td>State</td><td>Minimal</td><td>More complex (stacks)</td></tr>
 *   <tr><td>Use case</td><td>Building values bottom-up</td><td>Collecting info, side effects</td></tr>
 * </table>
 * 
 * <p><b>Why Use Listener?</b></p>
 * <ul>
 *   <li>Automatic traversal - less code for walking tree</li>
 *   <li>Clear separation - enter methods prepare, exit methods process</li>
 *   <li>Multiple listeners can observe same tree independently</li>
 *   <li>Natural for side effects like validation, logging, symbol tables</li>
 * </ul>
 * 
 * <p><b>Trade-offs:</b></p>
 * <ul>
 *   <li>✅ Pro: Automatic traversal, no need to call visit() explicitly</li>
 *   <li>✅ Pro: Good for multiple independent analyses</li>
 *   <li>❌ Con: More state management (stacks, flags)</li>
 *   <li>❌ Con: Less control over traversal order</li>
 *   <li>❌ Con: Can't return values - must use instance variables</li>
 * </ul>
 * 
 * @see RegisterDSLVisitor for the alternative Visitor (pull-based) implementation
 * @see org.antlr.v4.runtime.tree.ParseTreeWalker for the automatic walker
 */
public class RegisterDSLListener extends RegisterDSLBaseListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterDSLListener.class);

    /**
     * Stack to accumulate Query objects as we build them.
     * <p><b>Why a stack?</b> Because Listener can't return values from methods.
     * After exitDsl() builds a Query, it pushes it here for later retrieval.</p>
     */
    private final Stack<Query> queryStack = new Stack<>();
    
    /**
     * Stack to track field maps for nested DSL objects.
     * <p>Each time we enter a DSL, we push a new map. When we exit, we pop it.
     * This is necessary because the walker might be in multiple DSL contexts simultaneously.</p>
     */
    private final Stack<Map<String, String>> fieldMapStack = new Stack<>();
    
    /**
     * Current field map being populated as we walk through fields.
     * <p>As exitField() is called for each field, it adds entries to this map.
     * Example: {"entity" -> "sivilstand.sivilstand", "verdi" -> "gift"}</p>
     */
    private Map<String, String> currentFieldMap = new HashMap<>();
    
    /**
     * Collects field maps from each DSL when processing an array.
     * <p>For input: [{...}, {...}], exitDsl() adds each DSL's fields to this list.
     * Later, exitMultipleDsl() uses this list to build the combined query.</p>
     */
    private final List<Map<String, String>> dslList = new ArrayList<>();
    
    /**
     * Flag to distinguish single DSL from array of DSLs.
     * <p>Set to false in enterSingleDsl(), true in enterMultipleDsl().
     * Determines behavior in exitDsl(): build query immediately vs collect for later.</p>
     */
    private boolean isArrayMode = false;
    
    /**
     * Retrieves the final Query result after the walker completes.
     * <p><b>Listener Pattern:</b> Since methods can't return values, we accumulate
     * results in queryStack and retrieve them after walking is done.</p>
     * 
     * <p>Usage:
     * <pre>
     * ParseTreeWalker walker = new ParseTreeWalker();
     * walker.walk(listener, tree);
     * Query result = listener.getResult();  // Get the accumulated result
     * </pre>
     * 
     * @return the built Query object, or null if none was built
     */
    public Query getResult() {
        return queryStack.isEmpty() ? null : queryStack.peek();
    }

    /**
     * Called by walker when entering an array of DSL objects.
     * <p><b>Listener Event:</b> This is fired BEFORE walker visits any children.</p>
     * <p>We use enter methods to prepare state before children are processed.</p>
     * 
     * @param ctx the parse tree context (unused here, but available if needed)
     */
    @Override
    public void enterMultipleDsl(MultipleDslContext ctx) {
        // LISTENER PATTERN: Prepare state before children are visited
        // Set flag so exitDsl() knows to collect DSLs instead of building queries
        isArrayMode = true;
        // Clear any previous array data
        dslList.clear();
    }

    /**
     * Called by walker when exiting an array of DSL objects.
     * <p><b>Listener Event:</b> This is fired AFTER walker has visited all children.</p>
     * <p>By this point, exitDsl() has been called for each DSL and populated dslList.</p>
     * <p>We use exit methods to process accumulated state after children are done.</p>
     * 
     * <p><b>Transformation:</b></p>
     * <pre>
     * Input: [{"entity": "sivilstand.sivilstand", "verdi": "gift"}, 
     *         {"entity": "sivilstand.sivilstand", "verdi": "ugift"}]
     * 
     * After children visited: dslList = [{entity: "...", verdi: "gift"}, {entity: "...", verdi: "ugift"}]
     * 
     * exitMultipleDsl() builds:
     *   1. BoolQuery for each DSL in dslList
     *   2. Outer BoolQuery wrapping all individual queries
     *   3. NestedQuery wrapping the outer bool
     *   4. Pushes final Query to queryStack
     * </pre>
     * 
     * @param ctx the parse tree context
     */
    @Override
    public void exitMultipleDsl(MultipleDslContext ctx) {
        // LISTENER PATTERN: Process accumulated state after all children visited
        // By now, exitDsl() has been called for each DSL and filled dslList
        
        if (dslList.isEmpty()) {
            throw new IllegalArgumentException("DSL array cannot be empty");
        }

        // Step 1: Extract base path from first DSL (all must have same base path)
        String basePath = extractBasePath(dslList.get(0));

        // Step 2: Build a bool query for each collected DSL
        List<Query> dslQueries = new ArrayList<>();
        for (Map<String, String> dslFields : dslList) {
            Query dslBoolQuery = buildDslBoolQuery(dslFields);
            dslQueries.add(dslBoolQuery);
        }

        // Step 3: Wrap all individual bool queries in an outer bool query
        BoolQuery outerBoolQuery = BoolQuery.of(b -> b.must(dslQueries));

        // Step 4: Wrap the outer bool in a nested query
        NestedQuery nestedQuery = NestedQuery.of(n -> n
            .path("document." + basePath)
            .query(q -> q.bool(outerBoolQuery))
        );

        // Step 5: Push result to stack (can't return it because Listener methods are void)
        Query result = Query.of(q -> q.nested(nestedQuery));
        queryStack.push(result);
        LOGGER.info("Generated query (array): {}", result);
    }

    /**
     * Called by walker when entering a single DSL object.
     * <p><b>Listener Event:</b> Fired before visiting the child DSL node.</p>
     * 
     * @param ctx the parse tree context
     */
    @Override
    public void enterSingleDsl(SingleDslContext ctx) {
        // LISTENER PATTERN: Prepare state - set flag for single mode
        isArrayMode = false;
    }

    /**
     * Called by walker when entering a DSL object.
     * <p><b>Listener Event:</b> Fired before visiting fields.</p>
     * <p><b>Enter vs Exit:</b> Enter methods prepare, exit methods process.</p>
     * <p>Here we prepare a fresh map to collect field values.</p>
     * 
     * @param ctx the parse tree context for this DSL
     */
    @Override
    public void enterDsl(DslContext ctx) {
        // LISTENER PATTERN: Prepare state before children are visited
        // Create a new field map for this DSL object
        currentFieldMap = new HashMap<>();
        // Push to stack (important for nested DSL contexts, though rare here)
        fieldMapStack.push(currentFieldMap);
        // Now walker will automatically visit all children (fields)
        // Each exitField() call will populate currentFieldMap
    }

    /**
     * Called by walker when exiting a DSL object.
     * <p><b>Listener Event:</b> Fired after ALL children (fields) have been visited.</p>
     * <p>By this point, exitField() has been called for each field and populated currentFieldMap.</p>
     * 
     * <p><b>Two Modes:</b></p>
     * <ul>
     *   <li><b>Array mode (isArrayMode=true):</b> Collect DSL fields into dslList for later</li>
     *   <li><b>Single mode (isArrayMode=false):</b> Build Query immediately and push to stack</li>
     * </ul>
     * 
     * <p><b>Listener Pattern in Action:</b></p>
     * <pre>
     * 1. enterDsl() was called - prepared currentFieldMap
     * 2. Walker automatically visited all field children
     * 3. Each exitField() populated currentFieldMap
     * 4. Now exitDsl() is called - currentFieldMap is fully populated
     * 5. We process the complete data and either:
     *    - Collect it (array mode)
     *    - Build Query and push to stack (single mode)
     * </pre>
     * 
     * @param ctx the parse tree context for this DSL
     */
    @Override
    public void exitDsl(DslContext ctx) {
        // LISTENER PATTERN: Process accumulated state after all children visited
        // By now, all fields have been visited and currentFieldMap is populated
        
        // Pop the field map from stack
        Map<String, String> dslFields = fieldMapStack.pop();

        if (isArrayMode) {
            // Array mode: Just collect this DSL's fields for later processing
            // exitMultipleDsl() will use dslList to build the combined query
            dslList.add(new HashMap<>(dslFields));
        } else {
            // Single mode: Build the query immediately
            // This is the same logic as Visitor.visitDsl(), but triggered as an event
            
            // Step 1: Extract field values (populated by exitField() calls)
            String entity = stripQuotes(dslFields.get("entity"));
            String operator = stripQuotes(dslFields.get("operator"));
            String verdi = dslFields.get("verdi");
            String ergjeldende = dslFields.get("ergjeldende");

            // Validate required fields
            if (entity == null || verdi == null) {
                throw new IllegalArgumentException("Missing required fields: entity or verdi");
            }

            // Step 2: Determine query type (must vs must_not)
            boolean isNegation = "harIkke".equals(operator);

            // Step 3: Parse entity field
            String[] parts = entity.split("\\.", 2);
            String basePath = parts[0];
            String fullField = entity;

            // Step 4: Build term queries
            List<Query> termQueries = new ArrayList<>();
            termQueries.add(buildTermQuery("document." + fullField, verdi));

            if (ergjeldende != null) {
                termQueries.add(buildTermQuery("document." + basePath + ".ergjeldende", ergjeldende));
            }

            // Step 5: Wrap in bool query
            BoolQuery boolQuery;
            if (isNegation) {
                boolQuery = BoolQuery.of(b -> b.mustNot(termQueries));
            } else {
                boolQuery = BoolQuery.of(b -> b.must(termQueries));
            }

            // Step 6: Wrap in nested query
            NestedQuery nestedQuery = NestedQuery.of(n -> n
                .path("document." + basePath)
                .query(q -> q.bool(boolQuery))
            );

            // Step 7: Push result to stack (can't return it - Listener methods are void)
            Query result = Query.of(q -> q.nested(nestedQuery));
            queryStack.push(result);
            LOGGER.info("Generated query (single): {}", result);
        }
    }

    /**
     * Called by walker when exiting a field.
     * <p><b>Listener Event:</b> Fired after the field's value has been visited.</p>
     * <p>This is where we extract the field name and value and store them in currentFieldMap.</p>
     * 
     * <p><b>Listener Pattern:</b></p>
     * <pre>
     * For input: "entity": "sivilstand.sivilstand"
     * 
     * 1. enterField() is called (we don't override it, so nothing happens)
     * 2. Walker visits the value node automatically
     * 3. exitField() is called
     * 4. We extract: fieldName="entity", value="sivilstand.sivilstand"
     * 5. We store: currentFieldMap.put("entity", "sivilstand.sivilstand")
     * 6. Later, exitDsl() uses the populated currentFieldMap to build Query
     * </pre>
     * 
     * <p><b>Key Difference from Visitor:</b></p>
     * <ul>
     *   <li><b>Visitor:</b> Explicitly calls visitValue() to get the value</li>
     *   <li><b>Listener:</b> Value is already visited by walker, we just extract from ctx</li>
     * </ul>
     * 
     * @param ctx the parse tree context for this field
     */
    @Override
    public void exitField(FieldContext ctx) {
        // LISTENER PATTERN: Extract data after walker has visited children
        // Field name is the STRING token (left side of ":")
        String fieldName = stripQuotes(ctx.STRING().getText());
        // Value is from the value child (already visited by walker)
        String value = extractValue(ctx.value());
        // Store in current map (this is the side effect that exitDsl() depends on)
        currentFieldMap.put(fieldName, value);
    }

    private String extractValue(ValueContext ctx) {
        if (ctx instanceof StringValueContext) {
            return ((StringValueContext) ctx).STRING().getText();
        } else if (ctx instanceof BooleanValueContext) {
            return ((BooleanValueContext) ctx).BOOLEAN().getText();
        } else if (ctx instanceof NumberValueContext) {
            return ((NumberValueContext) ctx).NUMBER().getText();
        }
        return null;
    }

    private String extractBasePath(Map<String, String> dslFields) {
        String entity = stripQuotes(dslFields.get("entity"));
        if (entity == null) {
            throw new IllegalArgumentException("Missing required field: entity");
        }
        String[] parts = entity.split("\\.", 2);
        return parts[0];
    }

    private Query buildDslBoolQuery(Map<String, String> dslFields) {
        String entity = stripQuotes(dslFields.get("entity"));
        String operator = stripQuotes(dslFields.get("operator"));
        String verdi = dslFields.get("verdi");
        String ergjeldende = dslFields.get("ergjeldende");

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
