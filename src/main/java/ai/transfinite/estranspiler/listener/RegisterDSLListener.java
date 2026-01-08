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

public class RegisterDSLListener extends RegisterDSLBaseListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterDSLListener.class);

    // Stack to manage query building
    private final Stack<Query> queryStack = new Stack<>();
    private final Stack<Map<String, String>> fieldMapStack = new Stack<>();
    
    // Current field map for the DSL being processed
    private Map<String, String> currentFieldMap = new HashMap<>();
    
    // Storage for array of DSL contexts
    private final List<Map<String, String>> dslList = new ArrayList<>();
    private boolean isArrayMode = false;
    
    public Query getResult() {
        return queryStack.isEmpty() ? null : queryStack.peek();
    }

    @Override
    public void enterMultipleDsl(MultipleDslContext ctx) {
        isArrayMode = true;
        dslList.clear();
    }

    @Override
    public void exitMultipleDsl(MultipleDslContext ctx) {
        // All DSLs have been collected, now build the query
        if (dslList.isEmpty()) {
            throw new IllegalArgumentException("DSL array cannot be empty");
        }

        // Extract base path from first DSL
        String basePath = extractBasePath(dslList.get(0));

        // Build list of bool queries for each DSL
        List<Query> dslQueries = new ArrayList<>();
        for (Map<String, String> dslFields : dslList) {
            Query dslBoolQuery = buildDslBoolQuery(dslFields);
            dslQueries.add(dslBoolQuery);
        }

        // Build outer bool query with all DSL queries
        BoolQuery outerBoolQuery = BoolQuery.of(b -> b.must(dslQueries));

        // Build nested query
        NestedQuery nestedQuery = NestedQuery.of(n -> n
            .path("document." + basePath)
            .query(q -> q.bool(outerBoolQuery))
        );

        Query result = Query.of(q -> q.nested(nestedQuery));
        queryStack.push(result);
        LOGGER.info("Generated query (array): {}", result);
    }

    @Override
    public void enterSingleDsl(SingleDslContext ctx) {
        isArrayMode = false;
    }

    @Override
    public void enterDsl(DslContext ctx) {
        // Start fresh field map for this DSL
        currentFieldMap = new HashMap<>();
        fieldMapStack.push(currentFieldMap);
    }

    @Override
    public void exitDsl(DslContext ctx) {
        // Pop the field map
        Map<String, String> dslFields = fieldMapStack.pop();

        if (isArrayMode) {
            // In array mode, just collect the DSL fields
            dslList.add(new HashMap<>(dslFields));
        } else {
            // In single mode, build the query immediately
            String entity = stripQuotes(dslFields.get("entity"));
            String operator = stripQuotes(dslFields.get("operator"));
            String verdi = dslFields.get("verdi");
            String ergjeldende = dslFields.get("ergjeldende");

            if (entity == null || verdi == null) {
                throw new IllegalArgumentException("Missing required fields: entity or verdi");
            }

            // Determine which clause type to use based on operator
            boolean isNegation = "harIkke".equals(operator);

            // Parse the entity field to extract path and field name
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

            // Build nested query
            NestedQuery nestedQuery = NestedQuery.of(n -> n
                .path("document." + basePath)
                .query(q -> q.bool(boolQuery))
            );

            Query result = Query.of(q -> q.nested(nestedQuery));
            queryStack.push(result);
            LOGGER.info("Generated query (single): {}", result);
        }
    }

    @Override
    public void exitField(FieldContext ctx) {
        // Extract field name and value, store in current field map
        String fieldName = stripQuotes(ctx.STRING().getText());
        String value = extractValue(ctx.value());
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
