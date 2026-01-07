grammar RegisterDSL;

// ---- Parser Rules ----

dsl
    : LBRACE fields RBRACE
    ;

fields
    : field (COMMA field)*
    ;

field
    : STRING COLON value
    ;

value
    : STRING      # StringValue
    | BOOLEAN     # BooleanValue
    | NUMBER      # NumberValue
    ;

// ---- Lexer Rules ----

LBRACE      : '{' ;
RBRACE      : '}' ;
COLON       : ':' ;
COMMA       : ',' ;

BOOLEAN     : 'true' | 'false' ;
NUMBER      : '-'? [0-9]+ ('.' [0-9]+)? ;

STRING
    : '"' ( ~["\\\r\n] | '\\' . )* '"'
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
