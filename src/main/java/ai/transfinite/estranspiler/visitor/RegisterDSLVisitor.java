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

public class RegisterDSLVisitor extends RegisterDSLBaseVisitor<Query> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterDSLVisitor.class);

    private final Map<String, String> fieldMap = new HashMap<>();

    @Override
    public Query visitSingleDsl(SingleDslContext ctx) {
        // For single DSL, generate simple structure
        return visitDsl(ctx.dsl());
    }

    @Override
    public Query visitMultipleDsl(MultipleDslContext ctx) {
        // For array of DSLs, generate nested bool structure
        return visitDslArray(ctx.dslArray());
    }

    @Override
    public Query visitDslArray(DslArrayContext ctx) {
        // Process each DSL in the array
        List<DslContext> dslList = ctx.dsl();
        
        if (dslList.isEmpty()) {
            throw new IllegalArgumentException("DSL array cannot be empty");
        }

        // Extract base path from first DSL
        String basePath = extractBasePath(dslList.get(0));

        // Build list of bool queries for each DSL
        List<Query> dslQueries = new ArrayList<>();
        for (DslContext dsl : dslList) {
            Query dslBoolQuery = buildDslBoolQuery(dsl);
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

    @Override
    public Query visitDsl(DslContext ctx) {
        // Clear the field map for each DSL
        fieldMap.clear();

        // Visit all fields to populate the map
        visitFields(ctx.fields());

        // Extract values from the map
        String entity = stripQuotes(fieldMap.get("entity"));
        String operator = stripQuotes(fieldMap.get("operator"));
        String verdi = fieldMap.get("verdi");
        String ergjeldende = fieldMap.get("ergjeldende");

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
        
        // First term query: document.<entity> = <verdi>
        termQueries.add(buildTermQuery("document." + fullField, verdi));
        
        // Second term query: <basePath>.ergjeldende = <ergjeldende> (if present)
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

    @Override
    public Query visitFields(FieldsContext ctx) {
        // Visit all field children
        for (FieldContext field : ctx.field()) {
            visitField(field);
        }
        return null;
    }

    @Override
    public Query visitField(FieldContext ctx) {
        // Extract field name and value
        String fieldName = stripQuotes(ctx.STRING().getText());
        String value = visitValue(ctx.value());
        
        // Store in the map
        fieldMap.put(fieldName, value);
        
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
