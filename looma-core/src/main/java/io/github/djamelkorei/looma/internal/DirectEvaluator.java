package io.github.djamelkorei.looma.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.BaseStream;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionInvocationTargetException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.NumberUtils;
import org.springframework.util.ObjectUtils;

/**
 * Evaluates the expressions templates are made of without going through SpEL, whose general machinery (type
 * descriptors for every property read, method resolution, argument conversion) dominated the profile of real pages.
 *
 * <p>It understands the same grammar as SpEL for a subset: literals, variables, property and method chains
 * ({@code a.b.c()}, {@code a?.b}), {@code + - * / %}, comparisons, {@code && || !}, {@code and or not}, the
 * ternary, the elvis operator (Looma's {@code ??}) and inline lists and maps, with SpEL's precedence. Every rule is
 * ported from SpEL (Spring 6.1 to 7.0 agree on all of them): the numeric promotion of operators, equality and
 * ordering, string concatenation, which getter a property reads ({@code getX}, {@code isX}, record-style
 * {@code x()}), which overload a call picks, the sandbox checks, and the exceptions SpEL throws.
 *
 * <p><b>Every step runs once.</b> When a step is not covered exactly (an argument SpEL would convert, an overload choice
 * that needs SpEL's distance rules, a null target, a field instead of a getter, an unusual operand type...), SpEL
 * evaluates that one step, with the values already computed bound as variables. Earlier steps are never evaluated
 * again, so a getter or method is called once, as with SpEL alone. When the code a step calls throws, the exception
 * is the one SpEL would throw. Expressions outside the subset (indexing, projections and selections, {@code T()},
 * assignments...) are not compiled at all and SpEL evaluates them from the start.
 */
final class DirectEvaluator {

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
                node = new Variable(t.text);
            } else {
                return null;
            }
            while (peek().kind == Kind.DOT || peek().kind == Kind.SAFE_DOT) {
                boolean nullSafe = tokens.get(pos++).kind == Kind.SAFE_DOT;
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
                    node = new Call(node, name.text, args.toArray(new Node[0]), nullSafe);
                } else {
                    node = new Property(node, name.text, nullSafe);
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

    // ------------------------------------------------------------------------------------------------ steps

    private static final SpelExpressionParser STEP_PARSER = new SpelExpressionParser();

    /**
     * One step SpEL evaluates for us: a tiny expression such as {@code #t.name} or {@code #l + #r} whose variables are
     * the values the evaluator has already computed. Parsed on first use; SpEL expressions are safe to share.
     */
    private static final class Step {
        private final String spel;
        private final String[] names;
        private volatile Expression expression;

        Step(String spel, String... names) {
            this.spel = spel;
            this.names = names;
        }

        private Expression expression() {
            Expression e = expression;
            if (e == null) expression = e = STEP_PARSER.parseExpression(spel);
            return e;
        }

        private SimpleEvaluationContext context(Object[] values) {
            SimpleEvaluationContext context = Expressions.stepContext();
            for (int i = 0; i < names.length; i++) context.setVariable(names[i], values[i]);
            return context;
        }

        Object eval(Scope scope, Object... values) {
            return expression().getValue(context(values), scope);
        }

        /** The value with the type SpEL gives it (a getter's declared type, for example), for conditions. */
        TypedValue evalTyped(Scope scope, Object... values) {
            SpelExpression e = (SpelExpression) expression();
            return e.getAST().getTypedValue(new ExpressionState(context(values), new TypedValue(scope)));
        }

        /** For steps written against {@code #root}: the operand keeps the type SpEL would have given it. */
        Object evalOn(TypedValue root) {
            SpelExpression e = (SpelExpression) expression();
            return e.getAST().getValue(new ExpressionState(Expressions.stepContext(), root));
        }
    }

    private static final Step NOT = new Step("!#root");
    private static final Step AND_LEFT = new Step("#root and true");
    private static final Step AND_RIGHT = new Step("true and #root");
    private static final Step OR_LEFT = new Step("#root or false");
    private static final Step OR_RIGHT = new Step("false or #root");
    private static final Step CONDITION = new Step("#root ? true : false");
    private static final Step NEGATE = new Step("-#v", "v");
    private static final Step UNARY_PLUS = new Step("+#v", "v");
    private static final Step ELVIS = new Step("#v ?: #f", "v", "f");
    private static final Map<Kind, Step> BINARY = Map.of(
            Kind.PLUS, new Step("#l + #r", "l", "r"),
            Kind.MINUS, new Step("#l - #r", "l", "r"),
            Kind.STAR, new Step("#l * #r", "l", "r"),
            Kind.SLASH, new Step("#l / #r", "l", "r"),
            Kind.PERCENT, new Step("#l % #r", "l", "r"));

    /** Marks "SpEL decides this step" between {@link Members} and the nodes. */
    private static final Object SPEL = new Object();

    /** A value that can never come out of an expression, to see whether SpEL's elvis took its fallback. */
    private static final Object FALLBACK = new Object();

    /**
     * A boolean operand as SpEL reads it: Boolean as is, anything else converted (or rejected) by SpEL itself, with
     * the type SpEL would see, so that a conversion error names the same type.
     */
    private static boolean bool(Node operand, Step step, Scope scope) {
        Object c = operand.condition(scope);
        if (c instanceof Boolean b) return b;
        return (Boolean) step.evalOn(c instanceof Typed t ? t.value : new TypedValue(c));
    }

    /** A non-Boolean condition value together with the type SpEL gives it (only built when needed). */
    private record Typed(TypedValue value) {}

    private static Object raw(Object condition) {
        return condition instanceof Typed t ? t.value.getValue() : condition;
    }

    // ------------------------------------------------------------------------------------------------ nodes

    private abstract static class Node {
        /** The value, or the exception SpEL would throw. */
        abstract Object eval(Scope scope);

        /**
         * The value for a boolean operator: a Boolean, or a {@link Typed} when SpEL would carry a declared type with it
         * (property and method results), or the plain value.
         */
        Object condition(Scope scope) {
            return eval(scope);
        }
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

    private static final class Variable extends Node {
        final String name;
        Variable(String name) { this.name = name; }
        @Override Object eval(Scope scope) { return scope.get(name); } // what Accessors.ScopeAccessor reads
    }

    private static final class Property extends Node {
        private final Node target;
        private final String name;
        private final boolean nullSafe;
        private final Step step;

        Property(Node target, String name, boolean nullSafe) {
            this.target = target;
            this.name = name;
            this.nullSafe = nullSafe;
            this.step = new Step("#t" + (nullSafe ? "?." : ".") + name, "t");
        }

        @Override
        Object eval(Scope scope) {
            Object t = target.eval(scope);
            if (t == null && nullSafe) return null;
            if (t == null || (nullSafe && t instanceof Optional)) return step.eval(scope, t); // Spring 7 unwraps it
            Object value = Members.read(t, name);
            return value == SPEL ? step.eval(scope, t) : value;
        }

        @Override
        Object condition(Scope scope) {
            Object t = target.eval(scope);
            if (t == null && nullSafe) return null;
            if (t == null || (nullSafe && t instanceof Optional)) return new Typed(step.evalTyped(scope, t));
            Object value = Members.read(t, name);
            if (value == SPEL) return new Typed(step.evalTyped(scope, t));
            if (value instanceof Boolean) return value;
            return new Typed(new TypedValue(value, Members.propertyType(t, name, value)));
        }
    }

    private static final class Call extends Node {
        private final Node target;
        private final String name;
        private final Node[] args;
        private final boolean nullSafe;
        private final Step step;

        Call(Node target, String name, Node[] args, boolean nullSafe) {
            this.target = target;
            this.name = name;
            this.args = args;
            this.nullSafe = nullSafe;
            String[] names = new String[args.length + 1];
            StringBuilder spel = new StringBuilder("#t").append(nullSafe ? "?." : ".").append(name).append('(');
            names[0] = "t";
            for (int i = 0; i < args.length; i++) {
                names[i + 1] = "a" + i;
                spel.append(i == 0 ? "" : ", ").append("#a").append(i);
            }
            this.step = new Step(spel.append(')').toString(), names);
        }

        @Override
        Object eval(Scope scope) {
            Object t = target.eval(scope);
            Object[] values = new Object[args.length]; // SpEL evaluates the arguments even for a null target
            for (int i = 0; i < args.length; i++) values[i] = args[i].eval(scope);
            if (t == null && nullSafe) return null;
            if (t == null || (nullSafe && t instanceof Optional)
                    || Sandbox.deniedTarget(t) || Sandbox.deniedMember(name) || t instanceof Scope) {
                return delegate(scope, t, values);
            }
            Object value = Members.invoke(t, name, values);
            return value == SPEL ? delegate(scope, t, values) : value;
        }

        @Override
        Object condition(Scope scope) {
            Object t = target.eval(scope);
            Object[] values = new Object[args.length];
            for (int i = 0; i < args.length; i++) values[i] = args[i].eval(scope);
            if (t == null && nullSafe) return null;
            if (t == null || (nullSafe && t instanceof Optional)
                    || Sandbox.deniedTarget(t) || Sandbox.deniedMember(name) || t instanceof Scope) {
                return new Typed(step.evalTyped(scope, all(t, values)));
            }
            Object value = Members.invoke(t, name, values);
            if (value == SPEL) return new Typed(step.evalTyped(scope, all(t, values)));
            if (value instanceof Boolean) return value;
            return new Typed(new TypedValue(value, Members.returnType(t, name, values, value)));
        }

        private Object delegate(Scope scope, Object t, Object[] values) {
            return step.eval(scope, all(t, values));
        }

        private static Object[] all(Object t, Object[] values) {
            Object[] all = new Object[values.length + 1];
            all[0] = t;
            System.arraycopy(values, 0, all, 1, values.length);
            return all;
        }
    }

    private static final class Not extends Node {
        private final Node operand;
        Not(Node operand) { this.operand = operand; }
        @Override Object eval(Scope scope) {
            return !bool(operand, NOT, scope);
        }
    }

    /** SpEL's {@code OpAnd}: the right operand is only evaluated when the left one is true. */
    private static final class And extends Node {
        private final Node left;
        private final Node right;
        And(Node left, Node right) { this.left = left; this.right = right; }
        @Override Object eval(Scope scope) {
            if (!bool(left, AND_LEFT, scope)) return Boolean.FALSE;
            return bool(right, AND_RIGHT, scope);
        }
    }

    /** SpEL's {@code OpOr}: the right operand is only evaluated when the left one is false. */
    private static final class Or extends Node {
        private final Node left;
        private final Node right;
        Or(Node left, Node right) { this.left = left; this.right = right; }
        @Override Object eval(Scope scope) {
            if (bool(left, OR_LEFT, scope)) return Boolean.TRUE;
            return bool(right, OR_RIGHT, scope);
        }
    }

    private static final class Ternary extends Node {
        private final Node condition;
        private final Node yes;
        private final Node no;
        Ternary(Node condition, Node yes, Node no) { this.condition = condition; this.yes = yes; this.no = no; }
        @Override Object eval(Scope scope) {
            return bool(condition, CONDITION, scope) ? yes.eval(scope) : no.eval(scope);
        }

        @Override Object condition(Scope scope) { // SpEL's ternary passes the chosen branch's value on, type included
            return bool(condition, CONDITION, scope) ? yes.condition(scope) : no.condition(scope);
        }
    }

    /** SpEL's {@code Elvis}: the left value unless it is null or the empty String. Spring 7 unwraps Optional, 6 does not. */
    private static final class Elvis extends Node {
        private final Node value;
        private final Node fallback;
        Elvis(Node value, Node fallback) { this.value = value; this.fallback = fallback; }
        @Override Object eval(Scope scope) {
            Object v = value.eval(scope);
            if (v instanceof Optional) {
                Object chosen = ELVIS.eval(scope, v, FALLBACK); // the fallback expression itself runs only if needed
                return chosen == FALLBACK ? fallback.eval(scope) : chosen;
            }
            return v != null && !"".equals(v) ? v : fallback.eval(scope);
        }

        @Override Object condition(Scope scope) { // SpEL's elvis passes the left value on with its type
            Object c = value.condition(scope);
            Object v = raw(c);
            if (v instanceof Optional) {
                Object chosen = ELVIS.eval(scope, v, FALLBACK);
                if (chosen == FALLBACK) return fallback.condition(scope);
                return chosen == v ? c : chosen; // Spring 6 keeps the Optional (and its type), Spring 7 unwraps it
            }
            return v != null && !"".equals(v) ? c : fallback.condition(scope);
        }
    }

    /** SpEL's {@code OpEQ} / {@code OpNE}. */
    private static final class Equals extends Node {
        private final Node left;
        private final Node right;
        private final boolean negate;
        Equals(Node left, Node right, boolean negate) { this.left = left; this.right = right; this.negate = negate; }
        @Override Object eval(Scope scope) {
            Object l = left.eval(scope);
            Object r = right.eval(scope);
            return Operators.equal(l, r) != negate;
        }
    }

    /** SpEL's {@code OpLT}, {@code OpLE}, {@code OpGT} and {@code OpGE}. */
    private static final class Compare extends Node {
        private final Node left;
        private final Node right;
        private final Kind op;
        Compare(Node left, Node right, Kind op) { this.left = left; this.right = right; this.op = op; }
        @Override Object eval(Scope scope) {
            Object l = left.eval(scope);
            Object r = right.eval(scope);
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
            Object r = right.eval(scope);
            if (l instanceof Number a && r instanceof Number b) return Operators.arithmetic(a, b, op);
            if (op == Kind.PLUS) {
                Object text = Operators.concatenate(l, r);
                if (text != SPEL) return text;
            }
            return BINARY.get(op).eval(scope, l, r); // string tricks, operator overloading, errors
        }
    }

    /** SpEL's unary {@code OpMinus}. */
    private static final class Negate extends Node {
        final Node operand;
        Negate(Node operand) { this.operand = operand; }

        @Override
        Object eval(Scope scope) {
            Object v = operand.eval(scope);
            if (!(v instanceof Number number)) return NEGATE.eval(scope, v);
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
            return v instanceof Number ? v : UNARY_PLUS.eval(scope, v);
        }
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
            this.constant = constantItems ? Collections.unmodifiableList(build(null)) : null;
        }

        private List<Object> build(Scope scope) {
            List<Object> list = new ArrayList<>(items.length);
            for (Node item : items) list.add(item.eval(scope));
            return list;
        }

        @Override Object eval(Scope scope) {
            return constant != null ? constant : build(scope);
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
            this.constant = constantParts ? Collections.unmodifiableMap(build(null)) : null;
        }

        private Map<Object, Object> build(Scope scope) {
            Map<Object, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object k = keys[i].eval(scope);
                map.put(k, values[i].eval(scope));
            }
            return map;
        }

        @Override Object eval(Scope scope) {
            return constant != null ? constant : build(scope);
        }
    }

    // ------------------------------------------------------------------------------------------------ operators

    /** Ports of SpEL's operator semantics, including the exceptions it throws. */
    static final class Operators {

        private Operators() {}

        /** {@code Operator.equalityCheck} with SpEL's {@code StandardTypeComparator}. */
        static boolean equal(Object left, Object right) {
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
                return compareObjects(left, right) == 0;
            }
            return false;
        }

        /** {@code OpLT} / {@code OpLE} / {@code OpGT} / {@code OpGE}. */
        static boolean compare(Object left, Object right, Kind op) {
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
            return test(compareObjects(left, right), op);
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

        /** {@code StandardTypeComparator.compare} for two values that are not both numbers. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static int compareObjects(Object left, Object right) {
            if (left == null) return right == null ? 0 : -1;
            if (right == null) return 1;
            if (left instanceof Number l && right instanceof Number r) { // reachable from equality only
                if (l instanceof BigDecimal || r instanceof BigDecimal) return bigDecimal(l).compareTo(bigDecimal(r));
                if (l instanceof Double || r instanceof Double) return Double.compare(l.doubleValue(), r.doubleValue());
                if (l instanceof Float || r instanceof Float) return Float.compare(l.floatValue(), r.floatValue());
                if (l instanceof BigInteger || r instanceof BigInteger) return bigInteger(l).compareTo(bigInteger(r));
                if (l instanceof Long || r instanceof Long) return Long.compare(l.longValue(), r.longValue());
                if (l instanceof Integer || r instanceof Integer) return Integer.compare(l.intValue(), r.intValue());
                if (l instanceof Short || r instanceof Short) return Short.compare(l.shortValue(), r.shortValue());
                if (l instanceof Byte || r instanceof Byte) return Byte.compare(l.byteValue(), r.byteValue());
                return Double.compare(l.doubleValue(), r.doubleValue());
            }
            try {
                if (left instanceof Comparable comparable) return comparable.compareTo(right);
            } catch (ClassCastException ex) {
                throw new SpelEvaluationException(ex, SpelMessage.NOT_COMPARABLE, left.getClass(), right.getClass());
            }
            throw new SpelEvaluationException(SpelMessage.NOT_COMPARABLE, left.getClass(), right.getClass());
        }

        /** {@code OpPlus} / {@code OpMinus} / {@code OpMultiply} / {@code OpDivide} / {@code OpModulus} on numbers. */
        static Object arithmetic(Number l, Number r, Kind op) {
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
                    case SLASH -> a / b; // division by zero throws ArithmeticException, as in SpEL
                    default -> a % b;
                };
            }
            return doubles(l.doubleValue(), r.doubleValue(), op);
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

        /** {@code OpPlus} with a String operand, or {@link #SPEL} where SpEL's converter or overloading decides. */
        static Object concatenate(Object left, Object right) {
            String l;
            String r;
            if (left instanceof String a && right instanceof String b) {
                l = a;
                r = b;
            } else if (left instanceof String a) {
                r = asText(right);
                if (r == null) return SPEL;
                l = a;
            } else if (right instanceof String b) {
                l = asText(left);
                if (l == null) return SPEL;
                r = b;
            } else {
                return SPEL;
            }
            if (l.length() + r.length() > MAX_CONCATENATED_STRING_LENGTH) return SPEL; // SpEL reports it
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
         * A property read, or {@link #SPEL}. SpEL tries the map accessor first (present keys only), then the sandboxed
         * reflective accessor, which uses the first of {@code getX()}, {@code isX()} (boolean) and {@code x()}.
         */
        static Object read(Object target, String name) {
            if (target instanceof Map<?, ?> map) {
                try {
                    return map.containsKey(name) ? map.get(name) : SPEL;
                } catch (RuntimeException ex) { // what PropertyOrFieldReference makes of a failing accessor
                    throw new SpelEvaluationException(ex, SpelMessage.EXCEPTION_DURING_PROPERTY_READ, name, ex.getMessage());
                }
            }
            if (Sandbox.deniedTarget(target) || Sandbox.deniedMember(name) || target instanceof Scope) return SPEL;
            Class<?> type = target.getClass();
            if (type.isArray()) return SPEL;
            Map<String, Object> byName = GETTERS.get(type);
            Object getter = byName.get(name);
            if (getter == null) {
                getter = getter(type, name);
                byName.putIfAbsent(name, getter);
            }
            if (getter == NONE) return SPEL;
            try {
                return (Object) ((Getter) getter).handle.invokeExact(target);
            } catch (Throwable t) {
                throw getterFailed(name, t);
            }
        }

        private record Getter(MethodHandle handle, Method method) {}

        /**
         * The type SpEL's reflective accessor gives a value it read ({@code new TypeDescriptor(property).narrow(value)}),
         * after {@link #read} returned it; a map value has the value's own type.
         */
        static TypeDescriptor propertyType(Object target, String name, Object value) {
            if (target instanceof Map) return TypeDescriptor.forObject(value);
            Object getter = GETTERS.get(target.getClass()).get(name);
            if (!(getter instanceof Getter g)) return TypeDescriptor.forObject(value);
            return new TypeDescriptor(new org.springframework.core.convert.Property(target.getClass(), g.method, null, name))
                    .narrow(value);
        }

        /** The type SpEL's method executor gives a result ({@code MethodParameter(method, -1)}, narrowed). */
        static TypeDescriptor returnType(Object target, String name, Object[] args, Object value) {
            Object found = METHODS.get(target.getClass()).get(name + '/' + args.length);
            if (found instanceof Overload[] overloads) {
                for (Overload o : overloads) {
                    if (o.accepts(args)) return new TypeDescriptor(new MethodParameter(o.method, -1)).narrow(value);
                }
            }
            return TypeDescriptor.forObject(value);
        }

        /**
         * SpEL's exception for a getter that throws: the reflective accessor wraps it in an AccessException, which
         * {@code PropertyOrFieldReference} reports as {@code EXCEPTION_DURING_PROPERTY_READ}.
         */
        private static RuntimeException getterFailed(String name, Throwable t) {
            if (t instanceof VirtualMachineError e) throw e;
            AccessException access = new AccessException(
                    "Unable to access property '" + name + "' through getter method", new InvocationTargetException(t));
            return new SpelEvaluationException(access, SpelMessage.EXCEPTION_DURING_PROPERTY_READ, name, access.getMessage());
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
            return new Getter(h.asType(GETTER), m);
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

        /** A method call, or {@link #SPEL} unless exactly one overload accepts the arguments without conversion. */
        static Object invoke(Object target, String name, Object[] args) {
            Class<?> type = target.getClass();
            Map<String, Object> byKey = METHODS.get(type);
            String key = name + '/' + args.length;
            Object found = byKey.get(key);
            if (found == null) {
                found = overloads(type, name, args.length);
                byKey.putIfAbsent(key, found);
            }
            if (found == NONE) return SPEL;
            Overload chosen = null;
            for (Overload o : (Overload[]) found) {
                if (o.accepts(args)) {
                    if (chosen != null) return SPEL; // SpEL picks by exactness and distance
                    chosen = o;
                }
            }
            if (chosen == null || chosen.invoker == null || !chosen.passesUnconverted(args)) return SPEL;
            try {
                return (Object) chosen.invoker.invokeExact(target, args);
            } catch (Throwable t) {
                throw methodFailed(name, target, t);
            }
        }

        /**
         * SpEL's exception for a method that throws ({@code MethodReference.throwSimpleExceptionIfPossible}): a runtime
         * exception as it is, anything else wrapped.
         */
        private static RuntimeException methodFailed(String name, Object target, Throwable t) {
            if (t instanceof VirtualMachineError e) throw e;
            if (t instanceof RuntimeException e) return e;
            return new ExpressionInvocationTargetException(0, "A problem occurred when trying to execute method '" + name
                    + "' on object of type [" + target.getClass().getName() + "]", t);
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
            final Method method;
            final MethodHandle invoker;    // (Object target, Object[] args) -> Object; null if not callable
            final Class<?>[] accepts;      // boxed parameter types
            final boolean[] primitive;
            final boolean[] plainObject;   // declared as Object: SpEL hands any argument over unchanged
            final boolean[] convertible;   // a parameter type SpEL's converter rebuilds (collections, maps, arrays...)

            Overload(Method m, Class<?> type) {
                method = m;
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
