package pl.kretdb.query;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import java.util.Collections;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.Value;
import pl.kretdb.model.DB;
import pl.kretdb.model.Document;

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
            System.out.println("token: " + t);
            return t;
        }

        Optional<Token> match(Predicate<Token> predicate) {
            Token t = readNextToken();
            if (t != null && predicate.test(t))
                return Optional.of(t);
            nextToken = t;
            System.out.println("no match");
            return Optional.empty();
        }

        Optional<String> match(TokenType tt) {
            System.out.println("try match " + tt);
            return match(tt, t->true);
        }

        Optional<String> match(TokenType tt, Predicate<String> valuePredicate) {
            System.out.println("try match " + tt + " with predicate");
            return match(t -> { 
                    System.out.println("pred token: " + t);
                    return t.type == tt && valuePredicate.test(t.getValue());
                }).map(Token::getValue);
        }
    }

    public QueryContext parseQuery(DB db, String query) {
        QueryContext qc = new QueryContext(db);
        PushbackReader pbr = new PushbackReader(new StringReader(query));
        Lexer lexer = new Lexer(pbr);
        System.out.println("parseFrom");
        parseFrom(qc, lexer);
        System.out.println("parseWhere");
        parseWhere(qc, lexer);
        System.out.println("parseSelect");
        parseSelect(qc, lexer);
        System.out.println("after parse");
        if (lexer.readNextToken() != null) {
            throw failMatch("expected end of query").get();
        }

        return qc;
    }

    private static Set<String> KEYWORDS = Set.of("from", "where", "select", "group", "null");

    private Supplier<RuntimeException> failMatch(String msg) {
        return () -> new IllegalStateException(msg);
    }

    private void parseFrom(QueryContext qc, Lexer l) {
        l.setDocumentAllowed(true);
        System.out.println("match from");
        l.match(TokenType.SYMBOL, v -> v.equals("from")).orElseThrow(failMatch("from expected"));
        System.out.println("match sources");
        do { 
            System.out.println("try match star");
            Operator op = l.match(TokenType.STAR)
                .map(__ -> Operators.selectAll())
                .orElseGet(() -> l.match(TokenType.DOCUMENT)
                        .map(d -> new Document(d))
                        .map(Operators::selectByDocument)
                        .orElseGet(() -> l.match(TokenType.SYMBOL)
                            .map(Operators::selectByDocumentName)
                            .orElseThrow(failMatch("source symbol expected"))));
            System.out.println("register operator");
            qc.addPartial(op);
            var index = qc.lastIndex();
            System.out.println("try match alias");
            l.match(TokenType.SYMBOL, Predicate.not(KEYWORDS::contains))
                .ifPresent(alias -> qc.addPartial(Operators.project(index, attrs -> 
                                attrs.entrySet().stream()
                                .flatMap(e -> Stream.of(e, Map.entry(alias + "." + e.getKey(), e.getValue())))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))));
            if (index > 0) {
                System.out.println("register cartesian");
                qc.addPartial(Operators.cartesian(index-1, qc.lastIndex()));
            }
            System.out.println("source end");
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
        System.out.println("resolve val " + t);
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

    private static Pattern aliasPattern = Pattern.compile("[a-z][a-z_0-9]*");

    private void parseSelect(QueryContext qc, Lexer l) {
        System.out.println("match select");
        l.match(TokenType.SYMBOL, "select"::equals);

        var projection =
            l.match(TokenType.STAR)
                .map(
                    __ -> { System.out.println("matched star, projecting all");
                     return (UnaryOperator<Map<String, String>>)(ma -> 
                        ma.entrySet().stream()
                            .collect(Collectors.toMap(e -> extractFieldName(e.getKey()), Entry::getValue, (v1, v2) -> v1)));}) 
                .orElseGet(() -> {
                    System.out.println("match projection list");
                    Map<String, QueryFunction> alias2expression = new HashMap<>();
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
                    return ma -> alias2expression.entrySet().stream()
                        .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().apply(ma)));
                });

        qc.addPartial(Operators.project(qc.lastIndex(), projection));
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
        System.out.println("parse expression begin");
        Token token = l.readNextToken();

        QueryFunction expr;
    
        if (token.type == TokenType.LP) {
            System.out.println("[expr] parens");
            expr = parseExpression(l);
            l.match(TokenType.RP).orElseThrow(failMatch("')' expected"));
            System.out.println("[expr] parens end");
        } else if (token.type == TokenType.SYMBOL) {
            System.out.println("[expr] symbol");
            if (token.getValue().equals("not")) {
                System.out.println("[expr] not");
                var expr2 = parseExpression(l);
                expr = ma -> convert2String(!convert2Bool(expr2.apply(ma)));
                System.out.println("[expr] not end");
            } else if (token.getValue().equals("false")) {
                System.out.println("[expr] false");
                expr = __ -> convert2String(false);
            } else if (token.getValue().equals("true")) {
                System.out.println("[expr] true");
                expr = __ -> convert2String(true);
            } else if (l.match(TokenType.LP).isPresent()) {
                System.out.println("[expr] fun call");
                List<QueryFunction> args = new ArrayList<>();
                if (l.match(TokenType.RP).isEmpty())
                    args = parseExpressionList(l);
                l.match(TokenType.RP).orElseThrow(failMatch("')' expected"));
                var argsCp = args;
                expr = ma -> callBuiltin(token.getValue(), argsCp.stream().map(arg -> arg.apply(ma)).collect(Collectors.toList()));
                System.out.println("[expr] fun call end");
            } else {
                System.out.println("[expr] naked symbol");
                expr = resolveValue(token);
            }
        } else if (token.type == TokenType.STRING || token.type == TokenType.NUMBER) {
            System.out.println("[expr] string/number");
            expr = resolveValue(token);
        } else 
            throw failMatch("term expected").get();

        System.out.println("[expr] term matched");
        if (l.match(TokenType.SYMBOL, "in"::equals).isPresent())
            expr = parseInOperator(l, expr);
        else if (l.match(TokenType.SYMBOL, "is"::equals).isPresent()) {
            System.out.println("[expr] is (not) null");
            var expr2 = expr;
            expr = (QueryFunction)l.match(TokenType.SYMBOL, "null"::equals)
                .map(___ -> (QueryFunction)(ma -> convert2String(expr2.apply(ma) == null)))
                .orElseGet(() -> {
                    l.match(TokenType.SYMBOL, "not"::equals).orElseThrow(failMatch("'null' or 'not' expected"));
                    l.match(TokenType.SYMBOL, "null"::equals).orElseThrow(failMatch("'null' expected"));

                    return ma -> convert2String(expr2.apply(ma) != null);
                });
            System.out.println("[expr] is (not) null end");
        }

        System.out.println("parse expression op tail");
        var ret = parseOperatorExpressionTail(l, expr);
        System.out.println("parse expression begin");
        return ret;
    }

    // expects lexer after 'in' keyword
    private QueryFunction parseInOperator(Lexer l, QueryFunction first) {
        System.out.println("[expr] in");
        l.match(TokenType.LP).orElseThrow(failMatch("opening parethesis expected"));
        List<QueryFunction> matches = parseExpressionList(l);
        l.match(TokenType.RP).orElseThrow(failMatch("closing parethesis expected"));

        System.out.println("[expr] in end");
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
        System.out.println("[expr] op check");
        return l.match(OPERATORS::containsKey)
            .map(op -> {
                System.out.println("[expr] op");
                QueryFunction qf = parseExpression(l);
                var opFun = OPERATORS.get(op); 
                System.out.println("[expr] op end");
                return (QueryFunction)(ma -> opFun.apply(first.apply(ma), qf.apply(ma)));
            })
            .orElse(first);
    }

    private List<QueryFunction> parseExpressionList(Lexer l) {
        System.out.println("[expr] expression list");
        List<QueryFunction> args = new ArrayList<>();

        do {
            args.add(parseExpression(l));
        } while (l.match(TokenType.COMMA).isPresent());
        System.out.println("[expr] expression list end");
        return args;
    }

}
