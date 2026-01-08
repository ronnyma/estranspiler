# Visitor vs Listener Pattern for RegisterDSL

This document compares the two ANTLR tree traversal patterns implemented for the RegisterDSL transpiler.

## Overview

Both implementations transform RegisterDSL JSON into Elasticsearch Query objects using the Elasticsearch Java Client API with functional/lambda syntax. They produce identical output but use different traversal mechanisms.

## Visitor Pattern

**File:** `src/main/java/ai/transfinite/estranspiler/visitor/RegisterDSLVisitor.java`

### How it Works
- **Pull-based**: You explicitly call `visit()` on child nodes
- **Returns values**: Each visit method returns a `Query` object
- **Control flow**: You control when and which children to visit

### Key Characteristics
```java
@Override
public Query visitDsl(DslContext ctx) {
    // Explicitly visit children
    visitFields(ctx.fields());
    
    // Build and return query
    return Query.of(q -> q.nested(...));
}
```

### Advantages
- **Direct control**: You decide exactly when to visit children
- **Natural return values**: Methods can return computed results
- **Easier recursion**: Natural for building tree structures bottom-up
- **Less state management**: Results can be passed via return values

### When to Use
- Building AST transformations where you need computed values from children
- When you want fine-grained control over traversal order
- For transformations that are naturally recursive (like expression evaluation)

## Listener Pattern

**File:** `src/main/java/ai/transfinite/estranspiler/listener/RegisterDSLListener.java`

### How it Works
- **Push-based**: ANTLR's `ParseTreeWalker` automatically visits all nodes
- **Event-driven**: React to `enter` and `exit` events for each rule
- **State-based**: Use instance variables to accumulate results

### Key Characteristics
```java
@Override
public void enterDsl(DslContext ctx) {
    // Prepare state before children are visited
    currentFieldMap = new HashMap<>();
}

@Override
public void exitDsl(DslContext ctx) {
    // Process accumulated state after children visited
    Query result = buildQuery();
    queryStack.push(result);
}
```

### Advantages
- **Automatic traversal**: Walker handles all nodes automatically
- **Separation of concerns**: Each rule's logic is isolated
- **Easy to add observers**: Multiple listeners can observe the same tree
- **Natural for side effects**: Good for collecting information or validation

### When to Use
- When you want automatic depth-first traversal
- For collecting information across the tree (symbol tables, validation)
- When multiple independent analyses are needed (multiple listeners on same tree)
- For side-effect operations like code generation or logging

## Usage Examples

### Using Visitor
```java
CharStream input = CharStreams.fromString(dslJson);
RegisterDSLLexer lexer = new RegisterDSLLexer(input);
CommonTokenStream tokens = new CommonTokenStream(lexer);
RegisterDSLParser parser = new RegisterDSLParser(tokens);
ParseTree tree = parser.root();

RegisterDSLVisitor visitor = new RegisterDSLVisitor();
Query query = visitor.visit(tree);  // Returns Query directly
```

### Using Listener
```java
CharStream input = CharStreams.fromString(dslJson);
RegisterDSLLexer lexer = new RegisterDSLLexer(input);
CommonTokenStream tokens = new CommonTokenStream(lexer);
RegisterDSLParser parser = new RegisterDSLParser(tokens);
ParseTree tree = parser.root();

RegisterDSLListener listener = new RegisterDSLListener();
ParseTreeWalker walker = new ParseTreeWalker();
walker.walk(listener, tree);
Query query = listener.getResult();  // Get result from listener state
```

## Implementation Differences

### State Management

**Visitor:**
- Minimal state (just `fieldMap` for current DSL)
- Results passed via return values
- Less complex state tracking

**Listener:**
- More state management (`queryStack`, `fieldMapStack`, `dslList`, `isArrayMode`)
- Uses stacks to track nested contexts
- State accumulates during traversal

### Error Handling

Both implementations throw `IllegalArgumentException` for:
- Missing required fields (`entity` or `verdi`)
- Empty DSL arrays

### Generated Output

Both produce identical Elasticsearch Query objects:
```json
{
  "query": {
    "nested": {
      "path": "document.sivilstand",
      "query": {
        "bool": {
          "must": [
            {"term": {"document.sivilstand.sivilstand": {"value": "gift"}}},
            {"term": {"document.sivilstand.ergjeldende": {"value": true}}}
          ]
        }
      }
    }
  }
}
```

## Performance

- **Visitor**: Slightly more efficient (no walker overhead, explicit control)
- **Listener**: Minimal overhead from walker, negligible for most use cases

## Recommendation

**Use Visitor** when:
- You need direct control over traversal
- Building transformations with return values
- Implementing recursive algorithms

**Use Listener** when:
- You want automatic traversal
- Multiple analyses on the same tree
- Collecting information or doing validation
- Working with side effects

For this RegisterDSL transpiler, **Visitor is recommended** because:
1. Direct control over when children are visited
2. Natural fit for building Query objects bottom-up
3. Simpler state management
4. Return values make the data flow clearer

However, both implementations are fully functional and produce identical results!
