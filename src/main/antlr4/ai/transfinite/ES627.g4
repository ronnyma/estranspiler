grammar ES627;

query
 : expression TERMINATOR
 ;

 expression
  : expression '.' expression
  | function
  ;

function
  : boolQuery
  ;

boolQuery
  : 'boolQuery()' ('.' ('must'|'should') LPAREN (termQuery|nestedQuery|boolQuery) RPAREN)+
  ;

termQuery
  : 'new TermQueryBuilder('STRING', '(STRING|bool|IDENTIFIER)')'
  ;

nestedQuery
  : 'nestedQuery' LPAREN STRING ',' (boolQuery|nestedQuery) RPAREN
  ;

bool
 : TRUE
 | FALSE
 ;

TRUE         : 'true';
FALSE        : 'false';
SPACE      : [ \t\r\n]+ -> skip;
NUMBER     : ( [0-9]* '.' )? [0-9]+;
STRING     : '"' .*? '"';
IDENTIFIER : [a-zA-Z_][a-zA-Z_0-9]*;           // Matches variable names
LPAREN     : '(' ;                    // Open parenthesis
RPAREN     : ')' ;                    // Close parenthesis
TERMINATOR : ';';