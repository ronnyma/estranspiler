# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview
EStranspiler is a transpiler for Elasticsearch query DSL, converting from ES6 syntax (legacy method-chaining API) to ES7 syntax (lambda-based builder API). This serves as a backup solution in case the QueryGenerator becomes unstable due to increasing complexity.

**Example transformation:**
- ES6 (input): `boolQuery().must(termQuery("field", "value"))`
- ES7 (output): `BoolQuery.of(b -> b.must(s -> s.term(f -> f.field("field").value("value"))))`

## Technology Stack
- **Spring Boot 3.4.0** - Application framework
- **Java 21/22** - Target Java version
- **Maven** - Build system (wrapper included)
- **ANTLR 4.13.2** - Parser generator for ES6 DSL syntax
- **Elasticsearch Java Client** - Target output API (ES7 lambda builders)

## Build and Development Commands

### Build
```bash
./mvnw clean install      # Full build with tests
./mvnw package            # Build without installing to local repo
./mvnw compile            # Compile only (includes ANTLR generation)
```

### Testing
```bash
./mvnw test                                        # Run all tests
./mvnw test -Dtest=ES67visitorTest                # Run single test class
./mvnw test -Dtest=ES67visitorTest#parseQuery3    # Run single test method
```

### Running
```bash
./mvnw spring-boot:run    # Start Spring Boot application
```

### ANTLR Grammar Development
```bash
./mvnw generate-sources   # Generate parser/lexer from grammar (auto-runs during compile)
```

Generated ANTLR sources are placed in `target/generated-sources/antlr4/` and automatically added to the build path.

## Architecture

### Core Components
1. **Grammar Definition** (`src/main/antlr4/ai/transfinite/ES67.g4`)
   - Defines ES6 Elasticsearch query DSL syntax
   - Supports: `boolQuery()`, `termQuery()`, `matchQuery()`, `nestedQuery()`
   - Boolean clauses: `must()`, `should()`, `mustNot()`

2. **Visitor Pattern** (`src/main/java/ai/transfinite/estranspiler/visitor/ES67visitor.java`)
   - Extends ANTLR-generated `ES67BaseVisitor<String>`
   - Traverses parsed AST and emits ES7 lambda-based syntax
   - Key methods:
     - `visitBoolQuery()` - Transforms boolean query structure
     - `visitBoolClause()` - Converts must/should/mustNot clauses
     - `visitQueryExpr()` - Transforms term/match queries

3. **Spring Boot Application** (`EstranspilerApplication.java`)
   - Standard Spring Boot entry point (future REST API or CLI tool)

### Build Flow
1. ANTLR Maven plugin generates lexer/parser from `ES67.g4`
2. Generated code is compiled alongside hand-written Java
3. Spring Boot packages everything into executable JAR

### Code Organization
```
src/
├── main/
│   ├── antlr4/ai/transfinite/      # ANTLR grammar files
│   ├── java/ai/transfinite/estranspiler/
│   │   ├── visitor/                # Visitor implementations
│   │   └── EstranspilerApplication.java
│   └── resources/
│       └── application.properties
└── test/
    └── java/ai/transfinite/estranspiler/visitor/
        └── ES67visitorTest.java    # Parser/visitor tests
```

## Working with ANTLR Grammar

### Modifying Grammar
When editing `ES67.g4`:
1. Make changes to grammar rules
2. Run `./mvnw generate-sources` to regenerate parser/lexer
3. Update `ES67visitor.java` to handle new AST nodes
4. Add corresponding test cases in `ES67visitorTest.java`

### Grammar Structure
- **Parser rules** (lowercase): Define syntax structure (e.g., `boolQueryExpr`, `queryExpr`)
- **Lexer rules** (UPPERCASE): Define tokens (e.g., `BOOLQUERY`, `TERMQUERY`)

### Visitor Pattern Usage
The visitor transforms each AST node to ES7 syntax by:
1. Visiting each parse tree node
2. Recursively visiting child nodes
3. Assembling output strings with lambda syntax
4. Normalizing formatting (line breaks, indentation)

## Development Notes
- ANTLR-generated classes (`ES67Lexer`, `ES67Parser`, `ES67BaseVisitor`) are in `target/generated-sources/antlr4/`
- Never edit generated ANTLR files directly - modify `ES67.g4` instead
- The visitor returns `String` representations of ES7 code (could be enhanced to return AST objects)
- Tests in `ES67visitorTest` use ANTLR's `CharStream` and `CommonTokenStream` to parse test input
