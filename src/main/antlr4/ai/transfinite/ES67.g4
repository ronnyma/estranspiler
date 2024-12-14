grammar ES67;

// ---- Parser Rules ----

query
    : boolQueryExpr
    ;

boolQueryExpr
    // Matches a pattern like:
    // boolQuery() .must(...) .must(...)
    : BOOLQUERY LPAREN RPAREN ( DOT boolClause )*    # BoolQuery
    ;

boolClause
    // .must(<subQuery>)
    : MUST LPAREN subQueryExpr RPAREN
    | SHOULD LPAREN subQueryExpr RPAREN
    | MUSTNOT LPAREN subQueryExpr RPAREN
    ;

 subQueryExpr
    // A subQuery can be: termQuery, matchQuery, or nestedQuery
    : queryExpr
    ;

 queryExpr
    // termQuery("field", "value")
    : TERMQUERY LPAREN variable COMMA variable RPAREN
    | MATCHQUERY LPAREN variable COMMA variable RPAREN
    ;



matchQueryExpr
    // matchQuery("field", "value")
    : MATCHQUERY LPAREN variable COMMA variable RPAREN
    ;

scoremode
  : 'ScoreMode.Max'
  | 'ScoreMode.Min'
  ;

variable
    : identifier
    | stringLiteral
    ;

identifier
    : IDENTIFIER
    ;

stringLiteral
    : STRING
    ;



// ---- Lexer Rules ----

BOOLQUERY   : 'boolQuery' ;
NESTEDQUERY : 'nestedQuery' ;
TERMQUERY   : 'termQuery'
            | 'new TermQueryBuilder'
            ;
MATCHQUERY  : 'matchQuery' ;
MUST        : 'must' ;
SHOULD      : 'should' ;
MUSTNOT     : 'mustNot' ;
DOT         : '.' ;
COMMA       : ',' ;
LPAREN      : '(' ;
RPAREN      : ')' ;

// A simple string rule (double quotes).
STRING
    : '"' ( ~["\\] | '\\' . )* '"'
    ;
IDENTIFIER  : [A-Za-z][A-Z-a-z0-9]+;
// For matching "FOLK/2019/234567" or other possible tokens, we rely on STRING in the DSL.
WS
    : [ \t\r\n]+ -> skip
    ;

