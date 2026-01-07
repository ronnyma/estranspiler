grammar RegisterDSL;

// ---- Parser Rules ----

root
    : dsl           # SingleDsl
    | dslArray      # MultipleDsl
    ;

dslArray
    : LBRACKET dsl (COMMA dsl)* RBRACKET
    ;

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
LBRACKET    : '[' ;
RBRACKET    : ']' ;
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
