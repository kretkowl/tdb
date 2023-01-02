package pl.kretdb.query;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.Value;
import pl.kretdb.model.DB;
import pl.kretdb.model.Document;
import static pl.kretdb.Util.toMap;

public class QueryParser {

    @RequiredArgsConstructor
    static class PushbackReader extends Reader {

        private final Reader base;

        private int unread = -1;

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (len == 0)
                return 0;
            if(unread != -1) {
                cbuf[off] = (char) unread;
                unread = -1;
                return base.read(cbuf, off + 1, len - 1) + 1;
            } else
                return base.read(cbuf, off, len);
        }

        public void unread(int x) {
            if (x == -1)
                return;
            if (unread != -1)
                throw new IllegalStateException("already has unread character");
            unread = x;
        }

        @Override
        public void close() throws IOException {
            base.close();
        }
    }

    enum TokenType {
        SYMBOL, NUMBER, STRING, LP, RP, EQ, NE, GT, GE, LT, LE, MATCHES, COMMA, CONCAT, STAR, DOCUMENT
    }

    @AllArgsConstructor
    @Value
    @EqualsAndHashCode
    @ToString
    static class Token {
        TokenType type;
        String value;

        Token(TokenType type) {
            this.type = type;
            this.value = type.name();
        }
    }

    @RequiredArgsConstructor
    private static class Lexer {

        final PushbackReader pbr;
        @Setter
        private boolean documentAllowed;

        int readReq() throws IOException {
            int ch = pbr.read();
            if (ch == -1)
                throw new IllegalStateException("Unexpected end of file");
            return ch;
        }

        @SneakyThrows
        private Token readNextTokenInternal() {
            int ch;
            do {
                ch = pbr.read();
                if (ch == -1)
                    return null;
            } while (Character.isWhitespace(ch));
            if (documentAllowed && ch == '/') {
                StringBuilder sb = new StringBuilder();
                do {
                    sb.append((char) ch);
                    ch = pbr.read();
                } while (Character.isJavaIdentifierPart(ch)||ch=='/');
                pbr.unread(ch);
                return new Token(TokenType.DOCUMENT, sb.toString());

            } else if (ch == '\'') {
                StringBuilder sb = new StringBuilder();
                do {
                    ch = readReq();
                    if (ch == '\'') {
                        ch = pbr.read();
                        if (ch == '\'')
                            sb.append((char) ch);
                        else {
                            pbr.unread(ch);
                            return new Token(TokenType.STRING, sb.toString());
                        }
                    } else
                        sb.append((char) ch);
                } while (true);
            } else if (ch == '(') return new Token(TokenType.LP);
            else if (ch == ')') return new Token(TokenType.RP);
            else if (ch == '~') return new Token(TokenType.MATCHES);
            else if (ch == '=') return new Token(TokenType.EQ);
            else if (ch == ',') return new Token(TokenType.COMMA);
            else if (ch == '*') return new Token(TokenType.STAR);
            else if (ch == '!') {
                ch = readReq();
                if (ch != '=')
                    throw new IllegalStateException("= expected");
                return new Token(TokenType.EQ);
            } else if (ch == '|') {
                ch = readReq();
                if (ch != '|')
                    throw new IllegalStateException("| expected");
                return new Token(TokenType.CONCAT);
            } else if (ch == '<') {
                ch = pbr.read();
                if (ch == '=') 
                    return new Token(TokenType.LE);
                pbr.unread(ch);
                return new Token(TokenType.LT);
            } else if (ch == '>') {
                ch = pbr.read();
                if (ch == '=') 
                    return new Token(TokenType.GE);
                pbr.unread(ch);
                return new Token(TokenType.GT);
            } else if (ch >= '0' && ch <= '9') {
                int i = 0;
                while (ch >= '0' && ch <= '9') {
                    i = i*10 + ch - '0';
                    ch = pbr.read();
                }
                pbr.unread(ch);
                return new Token(TokenType.NUMBER, Integer.toString(i));
            } else {
                StringBuilder sb = new StringBuilder();
                do {
                    sb.append((char) ch);
                    ch = pbr.read();
                } while (Character.isJavaIdentifierPart(ch) || ch == '.');
                pbr.unread(ch);
                return new Token(TokenType.SYMBOL, sb.toString().toLowerCase());
            }
        }

        Token nextToken;

        Token readNextToken() {
            if (nextToken != null) {
                var ret = nextToken;
                nextToken = null;
                return ret;
            }
            var t = readNextTokenInternal();   
            return t;
        }

        Optional<Token> match(Predicate<Token> predicate) {
            Token t = readNextToken();
            if (t != null && predicate.test(t))
                return Optional.of(t);
            nextToken = t;
            return Optional.empty();
        }

        Optional<String> match(TokenType tt) {
            return match(tt, t->true);
        }

        Optional<String> match(TokenType tt, Predicate<String> valuePredicate) {
            return match(t -> { 
                    return t.type == tt && valuePredicate.test(t.getValue());
                }).map(Token::getValue);
        }
    }

    public QueryContext parseQuery(DB db, String query) {
        QueryContext qc = new QueryContext(db);
        PushbackReader pbr = new PushbackReader(new StringReader(query));
        Lexer lexer = new Lexer(pbr);
        parseQuery(qc, lexer);
        if (lexer.readNextToken() != null) {
            throw failMatch("expected end of query").get();
        }

        return qc;
    }

    private static Set<String> KEYWORDS = Set.of("from", "where", "select", "accumulate", "grouping", "null", "order");

    private Supplier<RuntimeException> failMatch(String msg) {
        return () -> new IllegalStateException(msg);
    }

    private void parseQuery(QueryContext qc, Lexer lexer) {
        parseFrom(qc, lexer);
        parseWhere(qc, lexer);
        parseGroup(qc, lexer);
        parseSelect(qc, lexer);
        parseOrder(qc, lexer);
    }

    private void parseFrom(QueryContext qc, Lexer l) {
        l.setDocumentAllowed(true);
        l.match(TokenType.SYMBOL, v -> v.equals("from")).orElseThrow(failMatch("from expected"));
        do { 
            var previousIndex = qc.lastIndex();
            l.match(TokenType.LP)
                .ifPresentOrElse(__ -> {
                    parseQuery(qc, l);
                    l.match(TokenType.RP).orElseThrow(failMatch("closing parenthesis expected"));
                },
                () -> {
                    Operator op = l.match(TokenType.STAR)
                        .map(__ -> Operators.selectAll())
                        .orElseGet(() -> l.match(TokenType.DOCUMENT)
                                .map(d -> new Document(d))
                                .map(Operators::selectByDocument)
                                .orElseGet(() -> l.match(TokenType.SYMBOL)
                                    .map(Operators::selectByDocumentName)
                                    .orElseThrow(failMatch("source symbol expected"))));
                    qc.addPartial(op);
                });
            l.match(TokenType.SYMBOL, Predicate.not(KEYWORDS::contains))
                .ifPresent(alias -> qc.addPartial(Operators.project(qc.lastIndex(), attrs -> 
                                attrs.entrySet().stream()
                                .flatMap(e -> Stream.of(e, Map.entry(alias + "." + e.getKey(), e.getValue())))
                                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))));
            if (previousIndex >= 0) {
                qc.addPartial(Operators.cartesian(previousIndex, qc.lastIndex()));
            }
        } while (l.match(TokenType.COMMA).isPresent());
        l.setDocumentAllowed(false);
    }

    private interface QueryFunction extends Function<Map<String, String>, String> {
        default String asLabel() { return null; }
    }

    private static boolean convert2Bool(String s) {
        return s != null && !"f".equalsIgnoreCase(s);
    }

    private static String convert2String(boolean b) {
        return b ? "t" : "f";
    }

    private String callBuiltin(String fun, List<String> args) {
        return null;
    }

    private QueryFunction resolveValue(Token t) {
        var type = t.getType();
        var value = t.getValue();
        if (type == TokenType.STRING || type == TokenType.NUMBER)
            return __ -> value;

        if (type == TokenType.SYMBOL)
            if ("null".equalsIgnoreCase(value)) return __ -> null;
            else if ("false".equalsIgnoreCase(value)) return __ -> "f";
            else if ("true".equalsIgnoreCase(value)) return __ -> "t";
            else return new QueryFunction() {

                public String apply(Map<String, String> qc) {
                    return qc.get(value);
                }

                @Override
                public String asLabel() {
                    return extractFieldName(value);
                }
            };
        throw new RuntimeException("shouldn't happen");
    }

    private void parseWhere(QueryContext qc, Lexer l) {
        if (l.match(TokenType.SYMBOL, "where"::equals).isEmpty())
            return;
        
        var function = parseExpression(l);
        Predicate<Map<String, String>> pred = ma -> convert2Bool(function.apply(ma));
        qc.addPartial(Operators.filter(qc.lastIndex(), pred));
    }

    private static Pattern fieldTail = Pattern.compile("[^.]+$");

    private static String extractFieldName(String key) {
        var matcher = fieldTail.matcher(key);
        if (!matcher.find()) {
            throw new RuntimeException("shouldn't happen");
        }
        return matcher.group();
    }

    private static final Comparator<String> QUERY_VALUE_COMPARATOR = new Comparator<String>() {

        public int compare(String v1, String v2) {
            if (v1 == null)
                if (v2 == null) return 0;
                else return 1;
            else if (v2 == null) return -1;

            return convert2Num(v1)
                .flatMap(n1 -> convert2Num(v2).map(n2 -> (int)(n1 - n2)))
                .orElseGet(() -> v1.compareTo(v2));
        }
    };
    private static Pattern aliasPattern = Pattern.compile("[a-z][a-z_0-9]*");

    private static Map<String, Function<Stream<String>, String>> groupingFunctions = Map.of(
            "count", list -> Long.toString(list.distinct().count()), 
            "max", list -> list.sorted(QUERY_VALUE_COMPARATOR.reversed()).findFirst().orElse(null),
            "min", list -> list.sorted(QUERY_VALUE_COMPARATOR).findFirst().orElse(null),
            "sum", list -> Long.toString(list.mapToLong(s -> convert2Num(s).orElse(0l)).sum()));
            //"join");

    private void parseGroup(QueryContext qc, Lexer l) {
        if (l.match(TokenType.SYMBOL, "accumulate"::equals).isEmpty())
            return;
        
        var aggregates = new HashMap<String, Function<List<Map<String, String>>, String>>();
        do {
            var fun = l.match(TokenType.SYMBOL, groupingFunctions::containsKey).orElseThrow(failMatch("aggregate function expected"));
            l.match(TokenType.LP).orElseThrow(failMatch("left parenthesis expected"));
            var expr = parseExpression(l);            
            l.match(TokenType.RP).orElseThrow(failMatch("left parenthesis expected"));
            var alias = l.match(TokenType.SYMBOL, s -> aliasPattern.matcher(s).matches()).orElseThrow(failMatch("alias expected"));
            aggregates.put(alias, list -> groupingFunctions.get(fun).apply(list.stream().map(expr)));
        } while (l.match(TokenType.COMMA).isPresent());

        var groupings = new LinkedList<String>();
        if (l.match(TokenType.SYMBOL, "grouping"::equals).isPresent()) {
            l.match(TokenType.SYMBOL, "by"::equals).orElseThrow(failMatch("by expected"));
            do {
                groupings.add(l.match(TokenType.SYMBOL).orElseThrow(failMatch("column name/alias expected")));
            } while (l.match(TokenType.COMMA).isPresent());
        }
        
        qc.addPartial(Operators.groupBy(
                    qc.lastIndex(), 
                    groupings, 
                    list -> aggregates.entrySet().stream().collect(
                        toMap(Entry::getKey, e -> e.getValue().apply(list)))));
    }

    private void parseSelect(QueryContext qc, Lexer l) {
        l.match(TokenType.SYMBOL, "select"::equals).orElseThrow(failMatch("select expected"));

        var projection =
            l.match(TokenType.STAR)
                .map(__ -> (UnaryOperator<Map<String, String>>)(ma -> 
                        ma.entrySet().stream()
                            .collect(toMap(e -> extractFieldName(e.getKey()), Entry::getValue)))) 
                .orElseGet(() -> {
                    Map<String, QueryFunction> alias2expression = parseSelectProjection(l);
                    return ma -> alias2expression.entrySet().stream()
                        .collect(toMap(
                                    Entry::getKey, 
                                    e -> e.getValue().apply(ma),
                                    (m1, m2) -> m2,
                                    LinkedHashMap::new));
                });

        qc.addPartial(Operators.project(qc.lastIndex(), projection));
    }

    private Map<String, QueryFunction> parseSelectProjection(Lexer l) {
        Map<String, QueryFunction> alias2expression = new LinkedHashMap<>();
        var aliasCnt = new AtomicInteger(0);
        do {
            var expr = parseExpression(l);
            var alias = l.match(TokenType.SYMBOL, v -> aliasPattern.matcher(v).matches())
                .orElseGet(() -> {
                    String evalAlias = null;
                    try {
                        evalAlias = expr.asLabel();
                    } catch (Exception e) { /* nop */ }
                    if (evalAlias == null || !aliasPattern.matcher(evalAlias).matches()) {
                        return "__data_" + aliasCnt.incrementAndGet();
                    }
                    return evalAlias;
                });
            alias2expression.put(alias, expr);
        } while (l.match(TokenType.COMMA).isPresent());
        return alias2expression;
    }

    /*
     * 
     * expr := 
     *    '(' expr ')'
     *    | 'not' expr
     *    | 'false'
     *    | 'true'
     *    | symbol '(' arg_list ')'
     *    | term
     *    | expr 'in' '(' const_list ')'
     *    | expr 'is' 'not'? 'null'
     *    | expr op expr
     * 
     * term := 
     *    number | string | symbol
     * 
     * op := EQ, NE, GT, GE, LT, LE, MATCHES, CONCAT, 'AND', 'OR'
     */
    private QueryFunction parseExpression(Lexer l) {
        Token token = l.readNextToken();

        QueryFunction expr;
    
        if (token.type == TokenType.LP) {
            expr = parseExpression(l);
            l.match(TokenType.RP).orElseThrow(failMatch("')' expected"));
        } else if (token.type == TokenType.SYMBOL) {
            if (token.getValue().equals("not")) {
                var expr2 = parseExpression(l);
                expr = ma -> convert2String(!convert2Bool(expr2.apply(ma)));
            } else if (token.getValue().equals("false")) {
                expr = __ -> convert2String(false);
            } else if (token.getValue().equals("true")) {
                expr = __ -> convert2String(true);
            } else if (l.match(TokenType.LP).isPresent()) {
                List<QueryFunction> args = new ArrayList<>();
                if (l.match(TokenType.RP).isEmpty())
                    args = parseExpressionList(l);
                l.match(TokenType.RP).orElseThrow(failMatch("')' expected"));
                var argsCp = args;
                expr = ma -> callBuiltin(token.getValue(), argsCp.stream().map(arg -> arg.apply(ma)).collect(Collectors.toList()));
            } else {
                expr = resolveValue(token);
            }
        } else if (token.type == TokenType.STRING || token.type == TokenType.NUMBER) {
            expr = resolveValue(token);
        } else 
            throw failMatch("term expected").get();

        if (l.match(TokenType.SYMBOL, "in"::equals).isPresent())
            expr = parseInOperator(l, expr);
        else if (l.match(TokenType.SYMBOL, "is"::equals).isPresent()) {
            var expr2 = expr;
            expr = (QueryFunction)l.match(TokenType.SYMBOL, "null"::equals)
                .map(___ -> (QueryFunction)(ma -> convert2String(expr2.apply(ma) == null)))
                .orElseGet(() -> {
                    l.match(TokenType.SYMBOL, "not"::equals).orElseThrow(failMatch("'null' or 'not' expected"));
                    l.match(TokenType.SYMBOL, "null"::equals).orElseThrow(failMatch("'null' expected"));

                    return ma -> convert2String(expr2.apply(ma) != null);
                });
        }

        var ret = parseOperatorExpressionTail(l, expr);
        return ret;
    }

    // expects lexer after 'in' keyword
    private QueryFunction parseInOperator(Lexer l, QueryFunction first) {
        l.match(TokenType.LP).orElseThrow(failMatch("opening parethesis expected"));
        List<QueryFunction> matches = parseExpressionList(l);
        l.match(TokenType.RP).orElseThrow(failMatch("closing parethesis expected"));

        return (ma) -> {
            var v = first.apply(ma);
            return convert2String(
                v != null
                && matches.stream().map(a -> a.apply(ma)).anyMatch(v::equals));
        };
    }

    private static Optional<Long> convert2Num(String s) {
        try {
            return Optional.of(Long.parseLong(s));
        } catch (NumberFormatException nfe) {
            return Optional.empty();
        }
    }

    private static BiFunction<String, String, String> numComparator(BiPredicate<Long, Long> numPredicate) {
        return (s1,s2) -> convert2Num(s1).flatMap(n1 -> convert2Num(s2).map(n2 -> numPredicate.test(n1, n2)))
                            .map(QueryParser::convert2String)
                            .orElse(null);
    }

    private static Map<Token, BiFunction<String, String, String>> OPERATORS = Map.of(
        new Token(TokenType.EQ), (s1,s2) -> convert2String(s1 == null ? s2 == null : s1.equals(s2)),
        new Token(TokenType.NE), (s1,s2) -> convert2String(s1 == null ? s2 != null : !s1.equals(s2)),
        new Token(TokenType.MATCHES), (s1,s2) -> convert2String(s1 == null && s2 == null && Pattern.matches(s2, s1)),
        new Token(TokenType.CONCAT), (s1,s2) -> (s1 == null ? "" : s1) + (s2 == null ? "" : s2),
        new Token(TokenType.GT), numComparator((n1,n2) -> n1 > n2),
        new Token(TokenType.GE), numComparator((n1,n2) -> n1 >= n2),
        new Token(TokenType.LT), numComparator((n1,n2) -> n1 < n2),
        new Token(TokenType.LE), numComparator((n1,n2) -> n1 <= n2),
        new Token(TokenType.SYMBOL, "and"), (s1,s2) -> convert2String(convert2Bool(s1) && convert2Bool(s2)),
        new Token(TokenType.SYMBOL, "or"), (s1,s2) -> convert2String(convert2Bool(s1) && convert2Bool(s2))
    );

    // expects lexer on operator
    private QueryFunction parseOperatorExpressionTail(Lexer l, QueryFunction first) {
        return l.match(OPERATORS::containsKey)
            .map(op -> {
                QueryFunction qf = parseExpression(l);
                var opFun = OPERATORS.get(op); 
                return (QueryFunction)(ma -> opFun.apply(first.apply(ma), qf.apply(ma)));
            })
            .orElse(first);
    }

    private List<QueryFunction> parseExpressionList(Lexer l) {
        List<QueryFunction> args = new ArrayList<>();

        do {
            args.add(parseExpression(l));
        } while (l.match(TokenType.COMMA).isPresent());
        return args;
    }

    private void parseOrder(QueryContext qc, Lexer l) {
        if (l.match(TokenType.SYMBOL, "order"::equals).isEmpty())
            return;
        l.match(TokenType.SYMBOL, "by"::equals).orElseThrow(failMatch("by expected"));

        var sortExpr = parseExpressionList(l).stream().map(e -> Comparator.comparing(e, QUERY_VALUE_COMPARATOR)).collect(Collectors.toList());

        var comparator = sortExpr.subList(1, sortExpr.size()).stream()
            .map(o -> (Comparator<Map<String, String>>) o)
            .reduce(sortExpr.get(0), (c1, c2) -> c1.thenComparing(c2));

        qc.addPartial(Operators.sort(qc.lastIndex(), comparator));
    }
}
