lexer grammar FundFlowLexer;

// ============================================================================
// Keywords (case-insensitive)
// ============================================================================

// Structural
RULE        : R U L E ;
LET         : L E T ;
WHEN        : W H E N ;
THEN        : T H E N ;
ELSE        : E L S E ;
IF          : I F ;

// Clause labels
DESCRIPTION : D E S C R I P T I O N ;
APPLIES     : A P P L I E S ;
EFFECTIVE   : E F F E C T I V E ;

// Verbs
ACCRUE      : A C C R U E ;
ALLOCATE    : A L L O C A T E ;
DISTRIBUTE  : D I S T R I B U T E ;
POST        : P O S T ;
PUBLISH     : P U B L I S H ;

// Connectors / prepositions
AS_OF       : A S WS_INLINE O F ;
TO          : T O ;
FROM        : F R O M ;
USING       : U S I N G ;
ON          : O N ;
OVER        : O V E R ;
BY          : B Y ;
ACROSS      : A C R O S S ;
AT          : A T ;
OF          : O F ;
EQUALLY     : E Q U A L L Y ;
WITH        : W I T H ;
THROUGH     : T H R O U G H ;
AS          : A S ;
IN          : I N ;

// Boundary words
START       : S T A R T ;
END         : E N D ;

// Top-level decls
IMPORT      : I M P O R T ;
MODULE      : M O D U L E ;
TYPE        : T Y P E ;
EXTENSION   : E X T E N S I O N ;
EXTENDS     : E X T E N D S ;
FIELD       : F I E L D ;
SCHEDULE    : S C H E D U L E ;
POLICY      : P O L I C Y ;
WATERFALL   : W A T E R F A L L ;

// Domain qualifiers
INCEPTION   : I N C E P T I O N ;
// `per annum` is matched as a single multi-word token so `per` alone falls
// through to IDENT and can appear inside noun phrases like
// `distribution per unit` or `nav per unit`.
PER_ANNUM   : P E R WS_INLINE A N N U M ;
NARRATIVE   : N A R R A T I V E ;

// Logic
AND         : A N D ;
OR          : O R ;
NOT         : N O T ;

// Phrasal aggregations (these are surface-syntactic forms, not function calls)
SUM         : S U M ;
WEIGHTED    : W E I G H T E D ;
AVERAGE     : A V E R A G E ;

// ============================================================================
// Domain literals
// ============================================================================

// MONEY_LITERAL: 3-letter ISO currency code + whitespace + amount with optional separators
// Examples: USD 1,250,000.00   EUR 50_000   JPY 1_000_000
MONEY_LITERAL
    : CURRENCY_CODE WS_INLINE AMOUNT_BODY
    ;

// DATE_LITERAL: YYYY-MM-DD (strict)
DATE_LITERAL
    : DIGIT DIGIT DIGIT DIGIT '-' DIGIT DIGIT '-' DIGIT DIGIT
    ;

// PCT_LITERAL: number immediately followed by %
PCT_LITERAL
    : NUMBER_BODY '%'
    ;

// BPS_LITERAL: number, whitespace, then 'bps' (case-insensitive)
BPS_LITERAL
    : NUMBER_BODY WS_INLINE B P S
    ;

// DAYCOUNT_LIT: actual/360, actual/365, 30/360, actual/actual
DAYCOUNT_LIT
    : (A C T U A L | '30') '/' ('360' | '365' | A C T U A L)
    ;

// Plain numeric token (used inside expressions, not for money/date/pct/bps)
NUMBER
    : NUMBER_BODY
    ;

// ============================================================================
// Identifiers and strings
// ============================================================================

IDENT
    : [a-zA-Z_] [a-zA-Z0-9_]*
    ;

// Quoted strings: also act as quoted identifiers (rule names, fund names, narratives)
STRING
    : '"' (~["\\\r\n] | '\\' .)* '"'
    ;

// ============================================================================
// Punctuation / operators
// ============================================================================

LBRACE      : '{' ;
RBRACE      : '}' ;
LPAREN      : '(' ;
RPAREN      : ')' ;
LBRACK      : '[' ;
RBRACK      : ']' ;
COLON       : ':' ;
SEMI        : ';' ;
COMMA       : ',' ;
DOT         : '.' ;
EQ          : '=' ;
PLUS        : '+' ;
MINUS       : '-' ;
STAR        : '*' ;
SLASH       : '/' ;
LT          : '<' ;
LE          : '<=' ;
GT          : '>' ;
GE          : '>=' ;
EQEQ        : '==' ;
NEQ         : '!=' ;

// ============================================================================
// Comments and whitespace (skipped)
// ============================================================================

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

// ============================================================================
// Fragments
// ============================================================================

fragment CURRENCY_CODE : [A-Z][A-Z][A-Z] ;
fragment AMOUNT_BODY   : DIGIT (DIGIT | '_' DIGIT | ',' DIGIT)* ('.' DIGIT+)? ;
fragment NUMBER_BODY   : DIGIT+ ('.' DIGIT+)? ;
fragment DIGIT         : [0-9] ;
fragment WS_INLINE     : [ \t\r\n]+ ;

// Case-insensitive letter fragments
fragment A : [aA] ; fragment B : [bB] ; fragment C : [cC] ;
fragment D : [dD] ; fragment E : [eE] ; fragment F : [fF] ;
fragment G : [gG] ; fragment H : [hH] ; fragment I : [iI] ;
fragment J : [jJ] ; fragment K : [kK] ; fragment L : [lL] ;
fragment M : [mM] ; fragment N : [nN] ; fragment O : [oO] ;
fragment P : [pP] ; fragment Q : [qQ] ; fragment R : [rR] ;
fragment S : [sS] ; fragment T : [tT] ; fragment U : [uU] ;
fragment V : [vV] ; fragment W : [wW] ; fragment X : [xX] ;
fragment Y : [yY] ; fragment Z : [zZ] ;
