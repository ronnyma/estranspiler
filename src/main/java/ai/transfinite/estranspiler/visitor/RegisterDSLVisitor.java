package ai.transfinite.estranspiler.visitor;

import ai.transfinite.RegisterDSLBaseVisitor;
import ai.transfinite.RegisterDSLParser.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RegisterDSLVisitor extends RegisterDSLBaseVisitor<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegisterDSLVisitor.class);

    private final Map<String, String> fieldMap = new HashMap<>();

    @Override
    public String visitDsl(DslContext ctx) {
        // Clear the field map for each DSL
        fieldMap.clear();

        // Visit all fields to populate the map
        visit(ctx.fields());

        // Extract values from the map
        String entity = stripQuotes(fieldMap.get("entity"));
        String verdi = fieldMap.get("verdi");
        String ergjeldende = fieldMap.get("ergjeldende");

        if (entity == null || verdi == null) {
            throw new IllegalArgumentException("Missing required fields: entity or verdi");
        }

        // Parse the entity field to extract path and field name
        // Example: sivilstand.sivilstand -> path: "sivilstand", full: "sivilstand.sivilstand"
        String[] parts = entity.split("\\.", 2);
        String basePath = parts[0];
        String fullField = entity;

        // Build the nested query
        StringBuilder query = new StringBuilder();
        query.append("{\n");
        query.append("  \"query\": {\n");
        query.append("    \"nested\": {\n");
        query.append("      \"path\": \"document.").append(basePath).append("\",\n");
        query.append("      \"query\": {\n");
        query.append("        \"bool\": {\n");
        query.append("          \"must\": [\n");
        
        // First term query: document.<entity> = <verdi>
        query.append("            { \"term\": { \"document.").append(fullField).append("\": ");
        query.append(verdi).append(" } }");

        // Second term query: document.<basePath>.ergjeldende = <ergjeldende> (if present)
        if (ergjeldende != null) {
            query.append(",\n");
            query.append("            { \"term\": { \"document.").append(basePath).append(".ergjeldende\": ");
            query.append(ergjeldende).append(" } }");
        }

        query.append("\n");
        query.append("          ]\n");
        query.append("        }\n");
        query.append("      }\n");
        query.append("    }\n");
        query.append("  }\n");
        query.append("}");

        String result = query.toString();
        LOGGER.info("Generated query:\n{}", result);
        return result;
    }

    @Override
    public String visitFields(FieldsContext ctx) {
        // Visit all field children
        ctx.field().forEach(this::visit);
        return null;
    }

    @Override
    public String visitField(FieldContext ctx) {
        // Extract field name and value
        String fieldName = stripQuotes(ctx.STRING().getText());
        String value = visit(ctx.value());
        
        // Store in the map
        fieldMap.put(fieldName, value);
        
        return null;
    }

    @Override
    public String visitStringValue(StringValueContext ctx) {
        // Return the string value with quotes
        return ctx.STRING().getText();
    }

    @Override
    public String visitBooleanValue(BooleanValueContext ctx) {
        // Return the boolean value as-is
        return ctx.BOOLEAN().getText();
    }

    @Override
    public String visitNumberValue(NumberValueContext ctx) {
        // Return the number value as-is
        return ctx.NUMBER().getText();
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
