grammar ES627;

query
 : expression TERMINATOR
 ;

 expression
  : expression '.' expression
  | queries
  ;

queries
  : termQuery
  | nestedQuery
  | boolQuery
  | rangeQuery
  | existsQuery
  ;

boolQuery
  : 'boolQuery()' ('.minimumShouldMatch' '(' NUMBER ')')? ('.' memberFunctions LPAREN queries RPAREN)+  (COMMA scoremode)?
  ;

rangeQuery
  : 'rangeQuery('STRING')' '.' intervals '(' STRING ')' # Range
  ;

memberFunctions
  : 'must'
  | 'should'
  | 'mustNot'
  ;

termQuery
  : 'new TermQueryBuilder('STRING','(STRING|bool|IDENTIFIER)('.'IDENTIFIER)*('()')*')'  # TermQry
  | 'termQuery('STRING','(STRING|bool|IDENTIFIER)('.'IDENTIFIER)*('()')*')'             # TermQry
  ;

existsQuery
  : 'new TermQueryBuilder(('STRING')'
  | 'existsQuery('STRING')'
  ;

nestedQuery
  : 'nestedQuery' LPAREN STRING ',' (boolQuery|nestedQuery) RPAREN
  ;

scoremode
  : 'ScoreMode.Max'
  | 'ScoreMode.Min'
  ;

intervals
  : 'lte'
  | 'gte'
  | 'gt'
  | 'lt'
  ;

bool
 : TRUE
 | FALSE
 ;


WS          : [ \t\r\n\u000C]+ -> skip;
TRUE        : 'true';
FALSE       : 'false';
NUMBER      : ( [0-9]* '.' )? [0-9]+;
STRING      : '"' .*? '"';
IDENTIFIER  : [a-zA-Z_][a-zA-Z_0-9]*;
LPAREN      : '(';
RPAREN      : ')';
COMMA       : ',';
TERMINATOR  : ';';
MINIMUMSHOULDMATCH  : 'minimumShouldMatch';