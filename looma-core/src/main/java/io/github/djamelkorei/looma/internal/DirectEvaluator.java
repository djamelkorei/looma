package io.github.djamelkorei.looma.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.BaseStream;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.NumberUtils;
import org.springframework.util.ObjectUtils;

/**
 * Evaluates the expressions templates are made of without going through SpEL, whose general machinery (type
 * descriptors for every property read, method resolution, argument conversion) dominated the profile of real pages.
 *
 * <p>It understands the same grammar as SpEL for a subset: literals, variables, property and method chains
 * ({@code a.b.c()}, {@code a?.b}), {@code + - * / %}, comparisons, {@code && || !}, {@code and or not}, the
 * ternary, the elvis operator (Looma's {@code ??}) and inline lists and maps, with SpEL's precedence. Every rule is ported from SpEL (Spring 6.1 to 7.0 agree on all of them): the
 * numeric promotion of operators, equality and ordering, string concatenation, which getter a property reads
 * ({@code getX}, {@code isX}, record-style {@code x()}), which overload a call picks, and the sandbox checks.
 *
 * <p>Whenever a case is not covered exactly (an unusual operand type, an argument SpEL would convert, an overload
 * choice that needs SpEL's distance rules, a null in the middle of a chain, any error) evaluation returns
 * {@link FastPath#MISS} and the caller evaluates the expression with SpEL, so results and error messages are the
 * same either way. Expressions outside the subset (indexing, projections and selections, bean references,
 * {@code T()}, assignments...) are not compiled at all.
 */
final class DirectEvaluator {

    private static final Object MISS = FastPath.MISS;
    private static final int MAX_CONCATENATED_STRING_LENGTH = 100_000; // SpEL's limit; beyond it SpEL reports an error

    private DirectEvaluator() {}

    /** A plan for {@code translated} (already in SpEL form), or null if it uses anything outside the subset. */
    static FastPath.Plan compile(String translated) {
        List<Token> tokens = Lexer.tokenize(translated);
        if (tokens == null) return null;
        Parser parser = new Parser(tokens);
        Node node = parser.expression();
        if (node == null || parser.peek().kind != Kind.EOF) return null;
        return node::eval;
    }

    // ------------------------------------------------------------------------------------------------ lexer

    private enum Kind {
        LITERAL, IDENT, AND, OR, NOT, EQ, NE, LT, LE, GT, GE, PLUS, MINUS, STAR, SLASH, PERCENT,
        QMARK, ELVIS, COLON, DOT, SAFE_DOT, LPAREN, RPAREN, LBRACE, RBRACE, COMMA, EOF
    }

    private record Token(Kind kind, String text, Object value) {}

    /** Identifiers SpEL reads as operators or special forms (case-insensitively); any of them ends compilation. */
    private static final Set<String> RESERVED = Set.of("eq", "ne", "lt", "gt", "le", "ge", "div", "mod",
            "instanceof", "matches", "between", "new", "t");

    private static final class Lexer {

        /** The tokens, or null if the text contains anything outside the subset. */
        static List<Token> tokenize(String s) {
            List<Token> out = new ArrayList<>();
            int i = 0;
            int n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                } else if (Character.isLetter(c) || c == '_') {
                    int start = i;
                    while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                    if (i < n && s.charAt(i) == '$') return null;
                    String word = s.substring(start, i);
                    String lower = word.toLowerCase(Locale.ROOT);
                    switch (lower) {
                        case "true" -> out.add(new Token(Kind.LITERAL, word, Boolean.TRUE));
                        case "false" -> out.add(new Token(Kind.LITERAL, word, Boolean.FALSE));
                        case "null" -> out.add(new Token(Kind.LITERAL, word, null));
                        case "and" -> out.add(new Token(Kind.AND, word, null));
                        case "or" -> out.add(new Token(Kind.OR, word, null));
                        case "not" -> out.add(new Token(Kind.NOT, word, null));
                        default -> {
                            if (RESERVED.contains(lower)) return null;
                            out.add(new Token(Kind.IDENT, word, null));
                        }
                    }
                } else if (Character.isDigit(c)) {
                    int start = i;
                    while (i < n && Character.isDigit(s.charAt(i))) i++;
                    Object value;
                    if (i + 1 < n && s.charAt(i) == '.' && Character.isDigit(s.charAt(i + 1))) {
                        i++;
                        while (i < n && Character.isDigit(s.charAt(i))) i++;
                        if (i < n && isNumberSuffix(s.charAt(i))) return null; // exponent, f, d...
                        value = Double.parseDouble(s.substring(start, i));
                    } else if (i < n && (s.charAt(i) == 'L' || s.charAt(i) == 'l')) {
                        try {
                            value = Long.parseLong(s.substring(start, i));
                        } catch (NumberFormatException e) {
                            return null;
                        }
                        i++;
                    } else {
                        if (i < n && isNumberSuffix(s.charAt(i))) return null; // hex, exponent, f, d...
                        try {
                            value = Integer.parseInt(s.substring(start, i));
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                    if (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) return null;
                    out.add(new Token(Kind.LITERAL, s.substring(start, i), value));
                } else if (c == '\'' || c == '"') {
                    StringBuilder text = new StringBuilder();
                    int j = i + 1;
                    while (true) {
                        if (j >= n) return null;
                        char d = s.charAt(j);
                        if (d == '\\') return null; // SpEL and Looma's translation scan backslashes differently
                        if (d == c) {
                            if (j + 1 < n && s.charAt(j + 1) == c) { // a doubled quote is one quote, as in SpEL
                                text.append(c);
                                j += 2;
                                continue;
                            }
                            break;
                        }
                        text.append(d);
                        j++;
                    }
                    out.add(new Token(Kind.LITERAL, s.substring(i, j + 1), text.toString()));
                    i = j + 1;
                } else {
                    char next = i + 1 < n ? s.charAt(i + 1) : 0;
                    Kind kind;
                    int length = 1;
                    switch (c) {
                        case '=' -> { if (next != '=') return null; kind = Kind.EQ; length = 2; }
                        case '!' -> { if (next == '=') { kind = Kind.NE; length = 2; } else kind = Kind.NOT; }
                        case '<' -> { if (next == '=') { kind = Kind.LE; length = 2; } else kind = Kind.LT; }
                        case '>' -> { if (next == '=') { kind = Kind.GE; length = 2; } else kind = Kind.GT; }
                        case '&' -> { if (next != '&') return null; kind = Kind.AND; length = 2; }
                        case '|' -> { if (next != '|') return null; kind = Kind.OR; length = 2; }
                        case '+' -> { if (next == '+') return null; kind = Kind.PLUS; }
                        case '-' -> { if (next == '-') return null; kind = Kind.MINUS; }
                        case '*' -> kind = Kind.STAR;
                        case '/' -> kind = Kind.SLASH;
                        case '%' -> kind = Kind.PERCENT;
                        case '?' -> {
                            if (next == '.') { kind = Kind.SAFE_DOT; length = 2; }
                            else if (next == ':') { kind = Kind.ELVIS; length = 2; }
                            else if (next == '[') return null; // selection
                            else kind = Kind.QMARK;
                        }
                        case ':' -> kind = Kind.COLON;
                        case '.' -> kind = Kind.DOT;
                        case '(' -> kind = Kind.LPAREN;
                        case ')' -> kind = Kind.RPAREN;
                        case ',' -> kind = Kind.COMMA;
                        case '{' -> kind = Kind.LBRACE;
                        case '}' -> kind = Kind.RBRACE;
                        default -> { return null; } // # @ $ [ ] ^ and anything else
                    }
                    out.add(new Token(kind, s.substring(i, i + length), null));
                    i += length;
                }
            }
            out.add(new Token(Kind.EOF, "", null));
            return out;
        }

        private static boolean isNumberSuffix(char c) {
            return Character.isLetter(c) || c == '_';
        }
    }

    // ------------------------------------------------------------------------------------------------ parser

    /** SpEL's grammar for the subset; every method returns null for anything it does not support. */
    private static final class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Token peek() {
            return tokens.get(pos);
        }

        private boolean accept(Kind kind) {
            if (peek().kind == kind) {
                pos++;
                return true;
            }
            return false;
        }

        Node expression() {
            Node condition = or();
            if (condition == null) return null;
            if (accept(Kind.ELVIS)) {
                Node fallback = expression();
                return fallback == null ? null : new Elvis(condition, fallback);
            }
            if (accept(Kind.QMARK)) {
                Node yes = expression();
                if (yes == null || !accept(Kind.COLON)) return null;
                Node no = expression();
                return no == null ? null : new Ternary(condition, yes, no);
            }
            return condition;
        }

        private Node or() {
            Node left = and();
            while (left != null && accept(Kind.OR)) {
                Node right = and();
                left = right == null ? null : new Or(left, right);
            }
            return left;
        }

        private Node and() {
            Node left = relational();
            while (left != null && accept(Kind.AND)) {
                Node right = relational();
                left = right == null ? null : new And(left, right);
            }
            return left;
        }

        private Node relational() {
            Node left = sum();
            if (left == null) return null;
            Kind op = peek().kind;
            if (op == Kind.EQ || op == Kind.NE || op == Kind.LT || op == Kind.LE || op == Kind.GT || op == Kind.GE) {
                pos++;
                Node right = sum();
                if (right == null) return null;
                return op == Kind.EQ || op == Kind.NE ? new Equals(left, right, op == Kind.NE) : new Compare(left, right, op);
            }
            return left;
        }

        private Node sum() {
            Node left = product();
            while (left != null && (peek().kind == Kind.PLUS || peek().kind == Kind.MINUS)) {
                Kind op = tokens.get(pos++).kind;
                Node right = product();
                left = right == null ? null : new Arithmetic(left, right, op);
            }
            return left;
        }

        private Node product() {
            Node left = unary();
            while (left != null && (peek().kind == Kind.STAR || peek().kind == Kind.SLASH || peek().kind == Kind.PERCENT)) {
                Kind op = tokens.get(pos++).kind;
                Node right = unary();
                left = right == null ? null : new Arithmetic(left, right, op);
            }
            return left;
        }

        private Node unary() {
            if (accept(Kind.NOT)) {
                Node operand = unary();
                return operand == null ? null : new Not(operand);
            }
            if (accept(Kind.MINUS)) {
                Node operand = unary();
                return operand == null ? null : new Negate(operand);
            }
            if (accept(Kind.PLUS)) {
                Node operand = unary();
                return operand == null ? null : new UnaryPlus(operand);
            }
            return primary();
        }

        private Node primary() {
            Token t = peek();
            Node node;
            if (t.kind == Kind.LITERAL) {
                pos++;
                node = new Literal(t.value);
            } else if (t.kind == Kind.LPAREN) {
                pos++;
                node = expression();
                if (node == null || !accept(Kind.RPAREN)) return null;
            } else if (t.kind == Kind.LBRACE) {
                pos++;
                node = inline();
                if (node == null) return null;
            } else if (t.kind == Kind.IDENT) {
                pos++;
                if (peek().kind == Kind.LPAREN) return null; // a method on the root scope: the sandbox refuses it
                if (t.text.equals("_beans")) return null;     // bean calls may have side effects: SpEL only
                node = new Variable(t.text);
            } else {
                return null;
            }
            while (peek().kind == Kind.DOT || peek().kind == Kind.SAFE_DOT) {
                pos++;
                Token name = peek();
                if (name.kind != Kind.IDENT) return null;
                pos++;
                if (accept(Kind.LPAREN)) {
                    List<Node> args = new ArrayList<>();
                    if (!accept(Kind.RPAREN)) {
                        do {
                            Node arg = expression();
                            if (arg == null) return null;
                            args.add(arg);
                        } while (accept(Kind.COMMA));
                        if (!accept(Kind.RPAREN)) return null;
                    }
                    node = new Call(node, name.text, args.toArray(new Node[0]));
                } else {
                    node = new Property(node, name.text);
                }
            }
            return node;
        }

        /**
         * After '{': SpEL's inline list {@code {a, b}} (and {@code {}}) or inline map {@code {k: v}} (and
         * {@code {:}}). A bare name as a key is the name itself, as in SpEL.
         */
        private Node inline() {
            if (accept(Kind.RBRACE)) return new InlineList(new Node[0]);
            if (peek().kind == Kind.COLON && tokens.get(pos + 1).kind == Kind.RBRACE) {
                pos += 2;
                return new InlineMap(new Node[0], new Node[0]);
            }
            Node first = expression();
            if (first == null) return null;
            if (accept(Kind.COLON)) {
                List<Node> keys = new ArrayList<>();
                List<Node> values = new ArrayList<>();
                Node key = first;
                while (true) {
                    Node value = expression();
                    if (value == null) return null;
                    keys.add(key instanceof Variable v ? new Name(v.name) : key);
                    values.add(value);
                    if (accept(Kind.RBRACE)) break;
                    if (!accept(Kind.COMMA)) return null;
                    key = expression();
                    if (key == null || !accept(Kind.COLON)) return null;
                }
                return new InlineMap(keys.toArray(new Node[0]), values.toArray(new Node[0]));
            }
            List<Node> items = new ArrayList<>();
            items.add(first);
            while (accept(Kind.COMMA)) {
                Node item = expression();
                if (item == null) return null;
                items.add(item);
            }
            return accept(Kind.RBRACE) ? new InlineList(items.toArray(new Node[0])) : null;
        }
    }

    // ------------------------------------------------------------------------------------------------ nodes

    private abstract static class Node {
        /** The value, or {@link #MISS}. Never throws for anything but JVM errors. */
        abstract Object eval(Scope scope);
    }

    private static class Literal extends Node {
        final Object value;
        Literal(Object value) { this.value = value; }
        @Override Object eval(Scope scope) { return value; }
    }

    /** A bare name used as an inline map key: SpEL takes the name, not the variable's value. */
    private static final class Name extends Literal {
        Name(String name) { super(name); }
    }

    /** True for what SpEL's {@code InlineMap} / {@code InlineList} count as constant parts. */
    private static boolean isConstant(Node node, boolean key) {
        if (node instanceof Name) return key;
        if (node instanceof Literal) return true;
        if (node instanceof Negate n) return n.operand instanceof Literal l && l.value instanceof Number;
        if (node instanceof InlineList list) return list.constant != null;
        if (node instanceof InlineMap map) return map.constant != null;
        return false;
    }

    /**
     * SpEL's {@code InlineList}: a new {@code ArrayList} each time, or one unmodifiable list reused when every item is
     * constant.
     */
    private static final class InlineList extends Node {
        private final Node[] items;
        final Object constant;

        InlineList(Node[] items) {
            this.items = items;
            boolean constantItems = true;
            for (Node item : items) constantItems &= isConstant(item, false);
            Object built = constantItems ? build(null) : null;
            this.constant = built == null ? null : java.util.Collections.unmodifiableList((List<?>) built);
        }

        private Object build(Scope scope) {
            List<Object> list = new ArrayList<>(items.length);
            for (Node item : items) {
                Object v = item.eval(scope);
                if (v == MISS) return null;
                list.add(v);
            }
            return list;
        }

        @Override Object eval(Scope scope) {
            if (constant != null) return constant;
            Object list = build(scope);
            return list == null ? MISS : list;
        }
    }

    /**
     * SpEL's {@code InlineMap}: a new {@code LinkedHashMap} each time, or one unmodifiable map reused when every key
     * and value is constant.
     */
    private static final class InlineMap extends Node {
        private final Node[] keys;
        private final Node[] values;
        final Object constant;

        InlineMap(Node[] keys, Node[] values) {
            this.keys = keys;
            this.values = values;
            boolean constantParts = true;
            for (int i = 0; i < keys.length; i++) {
                constantParts &= isConstant(keys[i], true) && isConstant(values[i], false);
            }
            Object built = constantParts ? build(null) : null;
            this.constant = built == null ? null : java.util.Collections.unmodifiableMap((Map<?, ?>) built);
        }

        private Object build(Scope scope) {
            Map<Object, Object> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object k = keys[i].eval(scope);
                if (k == MISS) return null;
                Object v = values[i].eval(scope);
                if (v == MISS) return null;
                map.put(k, v);
            }
            return map;
        }

        @Override Object eval(Scope scope) {
            if (constant != null) return constant;
            Object map = build(scope);
            return map == null ? MISS : map;
        }
    }

    /** SpEL's {@code Elvis}: the left value unless it is null or the empty String. Spring 7 unwraps Optional, 6 does not. */
    private static final class Elvis extends Node {
        private final Node value;
        private final Node fallback;
        Elvis(Node value, Node fallback) { this.value = value; this.fallback = fallback; }
        @Override Object eval(Scope scope) {
            Object v = value.eval(scope);
            if (v == MISS || v instanceof Optional) return MISS;
            return v != null && !"".equals(v) ? v : fallback.eval(scope);
        }
    }

    private static final class Variable extends Node {
        final String name;
        Variable(String name) { this.name = name; }
        @Override Object eval(Scope scope) { return scope.get(name); } // what Accessors.ScopeAccessor reads
    }

    private static final class Property extends Node {
        private final Node target;
        private final String name;
        Property(Node target, String name) { this.target = target; this.name = name; }

        @Override
        Object eval(Scope scope) {
            Object t = target.eval(scope);
            if (t == MISS || t == null) return MISS; // a null target is an error (or null-safe): SpEL decides
            return Members.read(t, name);
        }
    }

    private static final class Call extends Node {
        private final Node target;
        private final String name;
        private final Node[] args;
        Call(Node target, String name, Node[] args) { this.target = target; this.name = name; this.args = args; }

        @Override
        Object eval(Scope scope) {
            Object t = target.eval(scope);
            if (t == MISS || t == null) return MISS;
            if (Sandbox.deniedTarget(t) || Sandbox.deniedMember(name) || t instanceof Scope) return MISS;
            Object[] values = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                Object v = args[i].eval(scope);
                if (v == MISS) return MISS;
                values[i] = v;
            }
            return Members.invoke(t, name, values);
        }
    }

    private static final class Not extends Node {
        private final Node operand;
        Not(Node operand) { this.operand = operand; }
        @Override Object eval(Scope scope) {
            return operand.eval(scope) instanceof Boolean b ? !b : MISS; // SpEL converts other values, or fails
        }
    }

    private static final class And extends Node {
        private final Node left;
        private final Node right;
        And(Node left, Node right) { this.left = left; this.right = right; }
        @Override Object eval(Scope scope) {
            if (!(left.eval(scope) instanceof Boolean l)) return MISS;
            if (!l) return Boolean.FALSE;
            return right.eval(scope) instanceof Boolean r ? r : MISS;
        }
    }

    private static final class Or extends Node {
        private final Node left;
        private final Node right;
        Or(Node left, Node right) { this.left = left; this.right = right; }
        @Override Object eval(Scope scope) {
            if (!(left.eval(scope) instanceof Boolean l)) return MISS;
            if (l) return Boolean.TRUE;
            return right.eval(scope) instanceof Boolean r ? r : MISS;
        }
    }

    private static final class Ternary extends Node {
        private final Node condition;
        private final Node yes;
        private final Node no;
        Ternary(Node condition, Node yes, Node no) { this.condition = condition; this.yes = yes; this.no = no; }
        @Override Object eval(Scope scope) {
            if (!(condition.eval(scope) instanceof Boolean c)) return MISS;
            return c ? yes.eval(scope) : no.eval(scope);
        }
    }

    /** SpEL's {@code Operator.equalityCheck}. */
    private static final class Equals extends Node {
        private final Node left;
        private final Node right;
        private final boolean negate;
        Equals(Node left, Node right, boolean negate) { this.left = left; this.right = right; this.negate = negate; }

        @Override
        Object eval(Scope scope) {
            Object l = left.eval(scope);
            if (l == MISS) return MISS;
            Object r = right.eval(scope);
            if (r == MISS) return MISS;
            Boolean equal = Operators.equal(l, r);
            return equal == null ? MISS : equal != negate;
        }
    }

    /** SpEL's {@code OpLT}, {@code OpLE}, {@code OpGT} and {@code OpGE}. */
    private static final class Compare extends Node {
        private final Node left;
        private final Node right;
        private final Kind op;
        Compare(Node left, Node right, Kind op) { this.left = left; this.right = right; this.op = op; }

        @Override
        Object eval(Scope scope) {
            Object l = left.eval(scope);
            if (l == MISS) return MISS;
            Object r = right.eval(scope);
            if (r == MISS) return MISS;
            return Operators.compare(l, r, op);
        }
    }

    private static final class Arithmetic extends Node {
        private final Node left;
        private final Node right;
        private final Kind op;
        Arithmetic(Node left, Node right, Kind op) { this.left = left; this.right = right; this.op = op; }

        @Override
        Object eval(Scope scope) {
            Object l = left.eval(scope);
            if (l == MISS) return MISS;
            Object r = right.eval(scope);
            if (r == MISS) return MISS;
            if (l instanceof Number a && r instanceof Number b) return Operators.arithmetic(a, b, op);
            if (op == Kind.PLUS) return Operators.concatenate(l, r);
            return MISS; // SpEL's string tricks and operator overloading
        }
    }

    /** SpEL's unary {@code OpMinus}. */
    private static final class Negate extends Node {
        final Node operand;
        Negate(Node operand) { this.operand = operand; }

        @Override
        Object eval(Scope scope) {
            Object v = operand.eval(scope);
            if (!(v instanceof Number number)) return MISS;
            if (number instanceof BigDecimal d) return d.negate();
            if (number instanceof BigInteger i) return i.negate();
            if (number instanceof Double) return 0 - number.doubleValue();
            if (number instanceof Float) return 0 - number.floatValue();
            if (number instanceof Long) return 0 - number.longValue();
            if (number instanceof Integer) return 0 - number.intValue();
            if (number instanceof Short) return 0 - number.shortValue();
            if (number instanceof Byte) return 0 - number.byteValue();
            return 0 - number.doubleValue();
        }
    }

    /** SpEL's unary {@code OpPlus}: numbers pass through, anything else goes to operator overloading. */
    private static final class UnaryPlus extends Node {
        private final Node operand;
        UnaryPlus(Node operand) { this.operand = operand; }
        @Override Object eval(Scope scope) {
            Object v = operand.eval(scope);
            return v instanceof Number ? v : MISS;
        }
    }

    // ------------------------------------------------------------------------------------------------ operators

    /** Ports of SpEL's operator semantics; null (or MISS) where SpEL would convert, overload or fail. */
    static final class Operators {

        private Operators() {}

        /** {@code Operator.equalityCheck} with SpEL's {@code StandardTypeComparator}; null if SpEL would fail. */
        static Boolean equal(Object left, Object right) {
            if (left instanceof Number l && right instanceof Number r) {
                if (l instanceof BigDecimal || r instanceof BigDecimal) {
                    return bigDecimal(l).compareTo(bigDecimal(r)) == 0;
                } else if (l instanceof Double || r instanceof Double) {
                    return l.doubleValue() == r.doubleValue();
                } else if (l instanceof Float || r instanceof Float) {
                    return l.floatValue() == r.floatValue();
                } else if (l instanceof BigInteger || r instanceof BigInteger) {
                    return bigInteger(l).compareTo(bigInteger(r)) == 0;
                } else if (l instanceof Long || r instanceof Long) {
                    return l.longValue() == r.longValue();
                } else if (l instanceof Integer || r instanceof Integer) {
                    return l.intValue() == r.intValue();
                } else if (l instanceof Short || r instanceof Short) {
                    return l.shortValue() == r.shortValue();
                } else if (l instanceof Byte || r instanceof Byte) {
                    return l.byteValue() == r.byteValue();
                }
                return l.doubleValue() == r.doubleValue();
            }
            if (left instanceof CharSequence && right instanceof CharSequence) {
                return left.toString().equals(right.toString());
            }
            if (left instanceof Boolean && right instanceof Boolean) {
                return left.equals(right);
            }
            if (ObjectUtils.nullSafeEquals(left, right)) {
                return true;
            }
            if (canCompare(left, right)) {
                Integer c = compareTo(left, right);
                return c == null ? null : c == 0;
            }
            return false;
        }

        /** {@code OpLT} / {@code OpLE} / {@code OpGT} / {@code OpGE}; MISS if SpEL would fail. */
        static Object compare(Object left, Object right, Kind op) {
            if (left instanceof Number l && right instanceof Number r) {
                if (l instanceof BigDecimal || r instanceof BigDecimal) {
                    return test(bigDecimal(l).compareTo(bigDecimal(r)), op);
                } else if (l instanceof Double || r instanceof Double) {
                    double a = l.doubleValue();
                    double b = r.doubleValue();
                    return op == Kind.LT ? a < b : op == Kind.LE ? a <= b : op == Kind.GT ? a > b : a >= b;
                } else if (l instanceof Float || r instanceof Float) {
                    float a = l.floatValue();
                    float b = r.floatValue();
                    return op == Kind.LT ? a < b : op == Kind.LE ? a <= b : op == Kind.GT ? a > b : a >= b;
                } else if (l instanceof BigInteger || r instanceof BigInteger) {
                    return test(bigInteger(l).compareTo(bigInteger(r)), op);
                } else if (l instanceof Long || r instanceof Long) {
                    return test(Long.compare(l.longValue(), r.longValue()), op);
                } else if (l instanceof Integer || r instanceof Integer) {
                    return test(Integer.compare(l.intValue(), r.intValue()), op);
                } else if (l instanceof Short || r instanceof Short) {
                    return test(Short.compare(l.shortValue(), r.shortValue()), op);
                } else if (l instanceof Byte || r instanceof Byte) {
                    return test(Byte.compare(l.byteValue(), r.byteValue()), op);
                }
                double a = l.doubleValue();
                double b = r.doubleValue();
                return op == Kind.LT ? a < b : op == Kind.LE ? a <= b : op == Kind.GT ? a > b : a >= b;
            }
            if ((op == Kind.LT || op == Kind.GT) && left instanceof CharSequence && right instanceof CharSequence) {
                left = left.toString(); // only OpLT and OpGT do this; OpLE and OpGE compare the objects as they are
                right = right.toString();
            }
            Integer c = compareTo(left, right);
            return c == null ? MISS : test(c, op);
        }

        private static boolean test(int c, Kind op) {
            return op == Kind.LT ? c < 0 : op == Kind.LE ? c <= 0 : op == Kind.GT ? c > 0 : c >= 0;
        }

        /** {@code StandardTypeComparator.canCompare}. */
        private static boolean canCompare(Object left, Object right) {
            if (left == null || right == null) return true;
            if (left instanceof Number && right instanceof Number) return true;
            if (left instanceof Comparable && right instanceof Comparable) {
                Class<?> ancestor = ClassUtils.determineCommonAncestor(left.getClass(), right.getClass());
                return ancestor != null && Comparable.class.isAssignableFrom(ancestor);
            }
            return false;
        }

        /** {@code StandardTypeComparator.compare} (numbers are handled by the callers); null where SpEL throws. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Integer compareTo(Object left, Object right) {
            if (left == null) return right == null ? 0 : -1;
            if (right == null) return 1;
            if (left instanceof Number || right instanceof Number) return null; // mixed: let SpEL decide
            if (!(left instanceof Comparable comparable)) return null;
            try {
                return comparable.compareTo(right);
            } catch (ClassCastException e) {
                return null;
            } catch (RuntimeException e) {
                return null; // a broken compareTo: SpEL reports it
            }
        }

        /** {@code OpPlus} / {@code OpMinus} / {@code OpMultiply} / {@code OpDivide} / {@code OpModulus} on numbers. */
        static Object arithmetic(Number l, Number r, Kind op) {
            try {
                if (l instanceof BigDecimal || r instanceof BigDecimal) {
                    BigDecimal a = bigDecimal(l);
                    BigDecimal b = bigDecimal(r);
                    return switch (op) {
                        case PLUS -> a.add(b);
                        case MINUS -> a.subtract(b);
                        case STAR -> a.multiply(b);
                        case SLASH -> a.divide(b, Math.max(a.scale(), b.scale()), RoundingMode.HALF_EVEN);
                        default -> a.remainder(b);
                    };
                } else if (l instanceof Double || r instanceof Double) {
                    return doubles(l.doubleValue(), r.doubleValue(), op);
                } else if (l instanceof Float || r instanceof Float) {
                    float a = l.floatValue();
                    float b = r.floatValue();
                    return switch (op) {
                        case PLUS -> a + b;
                        case MINUS -> a - b;
                        case STAR -> a * b;
                        case SLASH -> a / b;
                        default -> a % b;
                    };
                } else if (l instanceof BigInteger || r instanceof BigInteger) {
                    BigInteger a = bigInteger(l);
                    BigInteger b = bigInteger(r);
                    return switch (op) {
                        case PLUS -> a.add(b);
                        case MINUS -> a.subtract(b);
                        case STAR -> a.multiply(b);
                        case SLASH -> a.divide(b);
                        default -> a.remainder(b);
                    };
                } else if (l instanceof Long || r instanceof Long) {
                    long a = l.longValue();
                    long b = r.longValue();
                    return switch (op) {
                        case PLUS -> a + b;
                        case MINUS -> a - b;
                        case STAR -> a * b;
                        case SLASH -> a / b;
                        default -> a % b;
                    };
                } else if (isIntegerForNumericOp(l) || isIntegerForNumericOp(r)) {
                    int a = l.intValue();
                    int b = r.intValue();
                    return switch (op) {
                        case PLUS -> a + b;
                        case MINUS -> a - b;
                        case STAR -> a * b;
                        case SLASH -> a / b;
                        default -> a % b;
                    };
                }
                return doubles(l.doubleValue(), r.doubleValue(), op);
            } catch (ArithmeticException e) {
                return MISS; // division by zero and friends: SpEL reports it
            }
        }

        private static Object doubles(double a, double b, Kind op) {
            return switch (op) {
                case PLUS -> a + b;
                case MINUS -> a - b;
                case STAR -> a * b;
                case SLASH -> a / b;
                default -> a % b;
            };
        }

        private static boolean isIntegerForNumericOp(Number n) {
            return n instanceof Integer || n instanceof Short || n instanceof Byte;
        }

        /** {@code OpPlus} with a String operand; other values are converted to text the way SpEL's converter does. */
        static Object concatenate(Object left, Object right) {
            String l;
            String r;
            if (left instanceof String a && right instanceof String b) {
                l = a;
                r = b;
            } else if (left instanceof String a) {
                r = asText(right);
                if (r == null) return MISS;
                l = a;
            } else if (right instanceof String b) {
                l = asText(left);
                if (l == null) return MISS;
                r = b;
            } else {
                return MISS; // operator overloading or an error
            }
            if (l.length() + r.length() > MAX_CONCATENATED_STRING_LENGTH) return MISS;
            return l + r;
        }

        /** What SpEL's default conversion service makes of a non-String operand; null if not certain. */
        private static String asText(Object value) {
            if (value == null) return "null";
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte
                    || value instanceof Double || value instanceof Float || value instanceof BigDecimal
                    || value instanceof BigInteger || value instanceof Boolean || value instanceof Character) {
                return value.toString(); // ObjectToStringConverter
            }
            return null;
        }

        private static BigDecimal bigDecimal(Number n) {
            return NumberUtils.convertNumberToTargetClass(n, BigDecimal.class);
        }

        private static BigInteger bigInteger(Number n) {
            return NumberUtils.convertNumberToTargetClass(n, BigInteger.class);
        }
    }

    // ------------------------------------------------------------------------------------------------ members

    /** Property reads and method calls with the rules of Looma's SpEL context and the {@link Sandbox}. */
    static final class Members {

        private Members() {}

        private static final Object NONE = new Object();
        private static final MethodType GETTER = MethodType.methodType(Object.class, Object.class);
        private static final MethodType INVOKER = MethodType.methodType(Object.class, Object.class, Object[].class);

        /** Getter per class and property name, misses included; ClassValue lets classes (and loaders) be unloaded. */
        private static final ClassValue<Map<String, Object>> GETTERS = new ClassValue<>() {
            @Override protected Map<String, Object> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
        };

        /** Overloads per class, method name and argument count; NONE when SpEL's rules are needed. */
        private static final ClassValue<Map<String, Object>> METHODS = new ClassValue<>() {
            @Override protected Map<String, Object> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
        };

        /**
         * A property read. SpEL tries the map accessor first (present keys only), then the sandboxed reflective
         * accessor, which uses the first of {@code getX()}, {@code isX()} (boolean) and {@code x()}.
         */
        static Object read(Object target, String name) {
            if (target instanceof Map<?, ?> map) {
                return map.containsKey(name) ? map.get(name) : MISS;
            }
            if (Sandbox.deniedTarget(target) || Sandbox.deniedMember(name) || target instanceof Scope) return MISS;
            Class<?> type = target.getClass();
            if (type.isArray()) return MISS;
            Map<String, Object> byName = GETTERS.get(type);
            Object getter = byName.get(name);
            if (getter == null) {
                getter = getter(type, name);
                byName.putIfAbsent(name, getter);
            }
            if (getter == NONE) return MISS;
            try {
                return (Object) ((MethodHandle) getter).invokeExact(target);
            } catch (Throwable e) {
                if (e instanceof Error err && !(e instanceof LinkageError)) throw err; // never swallow OOM and friends
                return MISS; // SpEL reports a failing getter
            }
        }

        /** SpEL's {@code ReflectivePropertyAccessor.findGetterForProperty}, with Looma's sandbox restrictions. */
        private static Object getter(Class<?> type, String name) {
            if (Proxy.isProxyClass(type)) return NONE;
            String[] suffixes = suffixes(name);
            Method m = find(type, "get", suffixes, false);
            if (m == null) m = find(type, "is", suffixes, true);
            if (m == null) m = find(type, "", new String[] {name}, false);
            if (m == null || m.getReturnType() == void.class) return NONE; // a public field, or something odd
            MethodHandle h = handle(m, type);
            if (h == null) return NONE;
            if (Modifier.isStatic(m.getModifiers())) h = MethodHandles.dropArguments(h, 0, Object.class);
            return h.asType(GETTER);
        }

        /** {@code ReflectivePropertyAccessor.getPropertyMethodSuffixes}. */
        private static String[] suffixes(String name) {
            String suffix = name.length() > 1 && Character.isUpperCase(name.charAt(1)) ? name : capitalize(name);
            return !suffix.isEmpty() && Character.isUpperCase(suffix.charAt(0))
                    ? new String[] {suffix} : new String[] {suffix, capitalize(suffix)};
        }

        private static String capitalize(String s) {
            return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        /** {@code findMethodForProperty}: SpEL scans {@code getMethods()} with bridge methods last. */
        private static Method find(Class<?> type, String prefix, String[] suffixes, boolean booleanOnly) {
            Method[] methods = type.getMethods();
            for (String suffix : suffixes) {
                String wanted = prefix + suffix;
                Method bridge = null;
                for (Method m : methods) {
                    if (!m.getName().equals(wanted) || m.getParameterCount() != 0) continue;
                    Class<?> declaring = m.getDeclaringClass();
                    if (declaring == Object.class || declaring == Class.class) continue; // DataBindingPropertyAccessor
                    if (booleanOnly && m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) continue;
                    if (!m.isBridge()) return m;
                    if (bridge == null) bridge = m;
                }
                if (bridge != null) return bridge;
            }
            return null;
        }

        /** A method call, or MISS unless exactly one overload accepts the arguments without conversion. */
        static Object invoke(Object target, String name, Object[] args) {
            Class<?> type = target.getClass();
            Map<String, Object> byKey = METHODS.get(type);
            String key = name + '/' + args.length;
            Object found = byKey.get(key);
            if (found == null) {
                found = overloads(type, name, args.length);
                byKey.putIfAbsent(key, found);
            }
            if (found == NONE) return MISS;
            Overload chosen = null;
            for (Overload o : (Overload[]) found) {
                if (o.accepts(args)) {
                    if (chosen != null) return MISS; // SpEL picks by exactness and distance
                    chosen = o;
                }
            }
            if (chosen == null || chosen.invoker == null || !chosen.passesUnconverted(args)) return MISS;
            try {
                return (Object) chosen.invoker.invokeExact(target, args);
            } catch (Throwable e) {
                if (e instanceof Error err && !(e instanceof LinkageError)) throw err;
                return MISS; // SpEL reports the failure
            }
        }

        /**
         * The fixed-arity overloads SpEL could pick for this many arguments, with its candidate rules
         * ({@code Sandbox.Methods}: instance methods not declared by Object or Class) and bridge handling.
         * NONE if a varargs overload exists (it competes under rules not ported here) or the class is a proxy.
         */
        private static Object overloads(Class<?> type, String name, int argCount) {
            if (Proxy.isProxyClass(type)) return NONE;
            Set<Method> candidates = new LinkedHashSet<>();
            for (Method m : type.getMethods()) {
                if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers())) continue;
                Class<?> declaring = m.getDeclaringClass();
                if (declaring == Object.class || declaring == Class.class) continue;
                if (m.isVarArgs()) return NONE;
                if (m.getParameterCount() == argCount) candidates.add(BridgeMethodResolver.findBridgedMethod(m));
            }
            if (candidates.isEmpty()) return NONE;
            List<Overload> out = new ArrayList<>(candidates.size());
            for (Method m : candidates) out.add(new Overload(m, type));
            return out.toArray(new Overload[0]);
        }

        private static final class Overload {
            final MethodHandle invoker;    // (Object target, Object[] args) -> Object; null if not callable
            final Class<?>[] accepts;      // boxed parameter types
            final boolean[] primitive;
            final boolean[] plainObject;   // declared as Object: SpEL hands any argument over unchanged
            final boolean[] convertible;   // a parameter type SpEL's converter rebuilds (collections, maps, arrays...)

            Overload(Method m, Class<?> type) {
                Class<?>[] params = m.getParameterTypes();
                int n = params.length;
                accepts = new Class<?>[n];
                primitive = new boolean[n];
                plainObject = new boolean[n];
                convertible = new boolean[n];
                for (int i = 0; i < n; i++) {
                    Class<?> p = params[i];
                    accepts[i] = ClassUtils.resolvePrimitiveIfNecessary(p);
                    primitive[i] = p.isPrimitive();
                    plainObject[i] = p == Object.class;
                    convertible[i] = p.isArray() || Collection.class.isAssignableFrom(p) || Map.class.isAssignableFrom(p)
                            || Iterable.class.isAssignableFrom(p) || Optional.class.isAssignableFrom(p)
                            || BaseStream.class.isAssignableFrom(p);
                }
                MethodHandle h = handle(m, type);
                invoker = h == null ? null : h.asSpreader(Object[].class, n).asType(INVOKER);
            }

            /** SpEL's exact or close match ({@code ReflectionHelper.compareArguments}) for these values. */
            boolean accepts(Object[] args) {
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a == null ? primitive[i] : !accepts[i].isInstance(a)) return false;
                }
                return true;
            }

            /** False where SpEL's argument conversion could hand the method something other than the value itself. */
            boolean passesUnconverted(Object[] args) {
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a == null || plainObject[i]) continue;
                    if (convertible[i] || a instanceof Collection || a instanceof Map || a.getClass().isArray()) {
                        return false;
                    }
                }
                return true;
            }
        }

        /**
         * A handle for {@code m} as SpEL would invoke it: through a public interface method when the class is not
         * accessible (for example {@code List.size()} on a JDK-internal list), else made accessible.
         */
        private static MethodHandle handle(Method m, Class<?> type) {
            try {
                return MethodHandles.publicLookup().unreflect(m);
            } catch (IllegalAccessException ignored) {
                // not public or not exported: try the interface method
            }
            Method viaInterface = ClassUtils.getInterfaceMethodIfPossible(m, type);
            if (viaInterface != m) {
                try {
                    return MethodHandles.publicLookup().unreflect(viaInterface);
                } catch (IllegalAccessException ignored) {
                    // fall through
                }
            }
            try {
                if (m.trySetAccessible()) return MethodHandles.lookup().unreflect(m);
            } catch (IllegalAccessException | SecurityException ignored) {
                // SpEL would fail too, or succeed under rules we do not reproduce
            }
            return null;
        }
    }
}
