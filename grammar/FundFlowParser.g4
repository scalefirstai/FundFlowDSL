parser grammar FundFlowParser;

options { tokenVocab=FundFlowLexer; }

// ============================================================================
// Top-level program structure
// ============================================================================

program
    : moduleDecl? importDecl* topLevelDecl* EOF
    ;

moduleDecl
    : MODULE modulePath SEMI?
    ;

importDecl
    : IMPORT modulePath SEMI?
    ;

modulePath
    : IDENT (DOT IDENT)*
    ;

topLevelDecl
    : ruleDecl
    | scheduleDecl
    | waterfallDecl
    | policyDecl
    | typeDecl
    ;

// ============================================================================
// Rule
// ============================================================================

ruleDecl
    : RULE name=STRING LBRACE ruleClause* RBRACE
    ;

ruleClause
    : descriptionClause
    | appliesToClause
    | effectiveClause
    | letBinding
    | statement
    ;

descriptionClause
    : DESCRIPTION COLON STRING
    ;

appliesToClause
    : APPLIES TO COLON selectorPhrase
    ;

effectiveClause
    : EFFECTIVE COLON periodExpr
    ;

letBinding
    : LET name=IDENT EQ expression
    ;

// ============================================================================
// Statements
// ============================================================================

statement
    : accrueStmt
    | allocateStmt
    | distributeStmt
    | postStmt
    | publishStmt
    | whenStmt
    ;

accrueStmt
    : ACCRUE rate=expression ON basis=expression USING dayCount=dayCountExpr
    ;

allocateStmt
    : ALLOCATE amount=expression ACROSS targetSet=expression allocationMethod
    ;

allocationMethod
    : BY weight=expression
    | EQUALLY
    ;

distributeStmt
    : DISTRIBUTE amount=expression THROUGH WATERFALL waterfallName=STRING
    ;

postStmt
    : POST subject=expression? TO target=postTarget (WITH NARRATIVE narrative=STRING)?
    ;

postTarget
    : qualifiedRef
    ;

publishStmt
    : PUBLISH subject=expression (AS_OF dateExpr)?
    ;

whenStmt
    : WHEN cond=expression THEN thenBranch=statement (ELSE elseBranch=statement)?
    ;

// ============================================================================
// Selectors (applies-to targets)
// ============================================================================

selectorPhrase
    : qualifiedRef
    ;

// ============================================================================
// Periods and dates
// ============================================================================

periodExpr
    : FROM dateExpr (TO dateExpr)?       # explicitFromTo
    | FROM INCEPTION                     # fromInception
    | qualifiedRef                       # namedOrPhrasalPeriod
    ;

dateExpr
    : DATE_LITERAL                       # dateLiteralExpr
    | INCEPTION                          # inceptionDateExpr
    | qualifiedRef                       # phrasalDateExpr
    ;

dayCountExpr
    : DAYCOUNT_LIT                       # dayCountLiteral
    | qualifiedRef                       # dayCountReference
    ;

// ============================================================================
// Expressions (with precedence via left-recursion)
// ============================================================================

expression
    : expression AS_OF dateExpr                                              # asOfExpr
    | expression AT (START|END) OF periodExpr                                # atBoundaryExpr
    | expression OVER periodExpr (USING inner=dayCountExpr)?                 # overExpr
    | expression PER_ANNUM                                                   # perAnnumExpr
    | NOT expression                                                         # notExpr
    | expression op=(STAR|SLASH) expression                                  # mulDivExpr
    | expression op=(PLUS|MINUS) expression                                  # addSubExpr
    | expression op=(LT|LE|GT|GE|EQEQ|NEQ) expression                        # compareExpr
    | expression AND expression                                              # andExpr
    | expression OR expression                                               # orExpr
    | functionCall                                                           # funcCallExpr
    | aggregationCall                                                        # aggCallExpr
    | literal                                                                # literalExpr
    | qualifiedRef                                                           # nameExpr
    | LPAREN expression RPAREN                                               # parenExpr
    ;

functionCall
    : name=(IDENT | SUM | AVERAGE) LPAREN (expression (COMMA expression)*)? RPAREN
    ;

aggregationCall
    : SUM OF expression (BY expression)?                                     # sumOfExpr
    | WEIGHTED AVERAGE expression WEIGHTED BY expression                     # weightedAvgExpr
    ;

// ============================================================================
// Atoms
// ============================================================================

literal
    : MONEY_LITERAL
    | DATE_LITERAL
    | PCT_LITERAL
    | BPS_LITERAL
    | DAYCOUNT_LIT
    | NUMBER
    | STRING
    ;

// A noun phrase is a chain of identifier-like tokens separated only by whitespace.
// Used for tokens that read as a single name: 'opening nav', 'valuation date',
// 'Q1 2026', 'fund "Alpha"', etc.
nounPhrase
    : nounAtom+
    ;

// A qualified reference is one or more noun phrases joined by 'of': handles
// 'commitment of investor', 'opening nav of share class', 'investors of fund'.
qualifiedRef
    : nounPhrase (OF nounPhrase)*
    ;

nounAtom
    : IDENT
    | STRING
    | NUMBER
    ;

// ============================================================================
// Schedule / Waterfall / Policy (lightweight shells; refined in later WPs)
// ============================================================================

scheduleDecl
    : SCHEDULE name=STRING LBRACE ruleClause* RBRACE
    ;

waterfallDecl
    : WATERFALL name=STRING LBRACE waterfallBody* RBRACE
    ;

waterfallBody
    : letBinding
    | statement
    ;

policyDecl
    : POLICY name=STRING LBRACE ruleClause* RBRACE
    ;

// ============================================================================
// Type extensions (for WP-11 forward-compat; parses today, semantic later)
// ============================================================================

typeDecl
    : TYPE EXTENSION typeName=IDENT EXTENDS baseType=IDENT LBRACE fieldDecl* RBRACE
    ;

fieldDecl
    : FIELD name=IDENT COLON typeRef
    ;

typeRef
    : IDENT
    ;
