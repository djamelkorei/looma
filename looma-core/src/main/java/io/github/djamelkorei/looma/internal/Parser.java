package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.ast.Expr;
import io.github.djamelkorei.looma.ast.Node;
import io.github.djamelkorei.looma.internal.Lexer.Kind;
import io.github.djamelkorei.looma.internal.Lexer.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the AST from lexer tokens. */
public final class Parser {

    private static final String COMPONENT_END = "</x>";
    private static final Pattern FOREACH = Pattern.compile("(?s)^(.*\\S)\\s+as\\s+\\$?(\\w+)$");

    private static final Pattern FOR_INIT = Pattern.compile("(?s)^\\$?(\\w+)\\s*=(?!=)\\s*(.+)$");
    private static final Pattern FOR_STEP = Pattern.compile("(?s)^\\$?(\\w+)\\s*(\\+\\+|--|\\+=|-=)\\s*(.*)$");

    private final List<Token> tokens;
    private int pos;
    private int loopDepth;
    private int switchDepth;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static List<Node> parse(String source) {
        return new Parser(Lexer.lex(source)).block(Set.of());
    }

    /** Parses nodes until a directive named in {@code stops} (left unconsumed) or end of input. */
    private List<Node> block(Set<String> stops) {
        List<Node> nodes = new ArrayList<>();
        while (pos < tokens.size()) {
            Token t = tokens.get(pos);
            if (t.kind() == Kind.DIRECTIVE && stops.contains(t.text())) {
                return nodes;
            }
            if (t.kind() == Kind.COMPONENT_CLOSE && stops.contains(COMPONENT_END)) {
                return nodes;
            }
            pos++;
            switch (t.kind()) {
                case TEXT -> nodes.add(new Node.Text(t.text()));
                case ECHO -> nodes.add(new Node.Echo(Expressions.parse(t.text(), t.line()), false));
                case RAW -> nodes.add(new Node.Echo(Expressions.parse(t.text(), t.line()), true));
                case DIRECTIVE -> nodes.add(directive(t));
                case COMPONENT_OPEN -> nodes.add(component(t, false));
                case COMPONENT_SELF -> nodes.add(component(t, true));
                case COMPONENT_CLOSE ->
                        throw new LoomaException("Unexpected closing tag </x-" + t.text() + ">", t.line(), null);
            }
        }
        if (!stops.isEmpty()) {
            int line = tokens.isEmpty() ? 0 : tokens.get(tokens.size() - 1).line();
            throw new LoomaException("Unclosed block, expected one of " + stops, line, null);
        }
        return nodes;
    }

    private Node directive(Token t) {
        return switch (t.text()) {
            case "if" -> parseIf(t, t.args(), "endif", true);
            case "unless" -> parseIf(t, "!(" + t.args() + ")", "endunless", false);
            case "isset" -> parseIf(t, "(" + t.args() + ") != null", "endisset", false);
            case "auth" -> parseIf(t, "principal != null", "endauth", false);
            case "guest" -> parseIf(t, "principal == null", "endguest", false);
            case "switch" -> parseSwitch(t);
            case "parent" -> new Node.Parent();
            case "for" -> parseFor(t);
            case "while" -> new Node.While(expr(t.args(), t), loopBlock(Set.of("endwhile")));
            case "break" -> {
                if (loopDepth == 0 && switchDepth == 0) {
                    throw new LoomaException("@break can only be used inside a loop or @switch", t.line(), null);
                }
                yield new Node.Break(t.args() == null ? null : expr(t.args(), t));
            }
            case "continue" -> {
                if (loopDepth == 0) {
                    throw new LoomaException("@continue can only be used inside a loop", t.line(), null);
                }
                yield new Node.Continue(t.args() == null ? null : expr(t.args(), t));
            }
            case "csrf" -> new Node.Csrf();
            case "lang" -> {
                List<String> a = Args.split(t.args());
                yield new Node.Lang(expr(a.get(0), t), a.subList(1, a.size()).stream().map(x -> expr(x, t)).toList());
            }
            case "method" -> new Node.Method(expr(t.args(), t));
            case "push", "prepend" -> {
                String end = "end" + t.text();
                List<Node> body = block(Set.of(end));
                pos++;
                yield new Node.Push(Args.literal(t.args(), t.line()), body, t.text().equals("prepend"));
            }
            case "stack" -> new Node.Stack(Args.literal(t.args(), t.line()));
            case "foreach" -> parseForeach(t, false);
            case "forelse" -> parseForeach(t, true);
            case "include" -> {
                List<String> a = Args.split(t.args());
                yield new Node.Include(expr(a.get(0), t), a.size() > 1 ? expr(a.get(1), t) : null);
            }
            case "extends" -> new Node.Extends(expr(t.args(), t));
            case "section" -> parseSection(t);
            case "yield" -> {
                List<String> a = Args.split(t.args());
                yield new Node.Yield(Args.literal(a.get(0), t.line()), a.size() > 1 ? expr(a.get(1), t) : null);
            }
            default -> throw new LoomaException("Unexpected @" + t.text(), t.line(), null);
        };
    }

    private Node parseIf(Token t, String cond, String end, boolean allowElseif) {
        Set<String> stops = allowElseif ? Set.of("elseif", "else", end) : Set.of("else", end);
        List<Node.Branch> branches = new ArrayList<>();
        List<Node> orElse = List.of();
        branches.add(new Node.Branch(expr(cond, t), block(stops)));
        while (true) {
            Token n = tokens.get(pos++);
            if (n.text().equals(end)) {
                break;
            } else if (n.text().equals("elseif")) {
                branches.add(new Node.Branch(expr(n.args(), n), block(stops)));
            } else { // else
                orElse = block(Set.of(end));
                pos++;
                break;
            }
        }
        return new Node.If(branches, orElse);
    }

    private Node component(Token t, boolean selfClosing) {
        List<Node> body = List.of();
        if (!selfClosing) {
            body = block(Set.of(COMPONENT_END));
            Token close = tokens.get(pos++);
            if (!close.text().equals(t.text().split(":")[0]) && !close.text().equals(t.text())) {
                throw new LoomaException("Expected </x-" + t.text() + "> but found </x-" + close.text() + ">",
                        close.line(), null);
            }
        }
        List<Node.Attr> attrs = parseAttrs(t);
        String name = t.text();
        if (name.equals("slot") || name.startsWith("slot:")) {
            String slotName = name.startsWith("slot:") ? name.substring(5) : attrs.stream()
                    .filter(a -> a.name().equals("name")).findFirst()
                    .map(a -> a.value().source())
                    .orElseThrow(() -> new LoomaException("<x-slot> needs a name", t.line(), null));
            return new Node.Slot(slotName, body);
        }
        List<Node> main = new ArrayList<>();
        java.util.Map<String, List<Node>> slots = new java.util.LinkedHashMap<>();
        for (Node n : body) {
            if (n instanceof Node.Slot s) slots.put(s.name(), s.body());
            else main.add(n);
        }
        return new Node.Component(name, attrs, main, slots);
    }

    /** Parses {@code a="x" :b="expr" flag} into attributes; {@code :b} values are expressions. */
    private List<Node.Attr> parseAttrs(Token t) {
        String raw = t.args();
        List<Node.Attr> attrs = new ArrayList<>();
        int i = 0;
        int n = raw.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(raw.charAt(i))) i++;
            if (i >= n) break;
            int start = i;
            while (i < n && !Character.isWhitespace(raw.charAt(i)) && raw.charAt(i) != '=') i++;
            String name = raw.substring(start, i);
            String value = null;
            if (i < n && raw.charAt(i) == '=') {
                i++;
                if (i < n && (raw.charAt(i) == '"' || raw.charAt(i) == '\'')) {
                    char q = raw.charAt(i++);
                    int vs = i;
                    while (i < n && raw.charAt(i) != q) i++;
                    value = raw.substring(vs, i);
                    i++;
                } else {
                    int vs = i;
                    while (i < n && !Character.isWhitespace(raw.charAt(i))) i++;
                    value = raw.substring(vs, i);
                }
            }
            if (name.startsWith(":")) {
                attrs.add(new Node.Attr(name.substring(1), Expressions.parse(value == null ? "true" : value, t.line())));
            } else if (value == null) {
                attrs.add(new Node.Attr(name, Expressions.parse("true", t.line())));
            } else {
                attrs.add(new Node.Attr(name, new Expr(new org.springframework.expression.common.LiteralExpression(value), value, t.line())));
            }
        }
        return attrs;
    }

    private Node parseSwitch(Token t) {
        var subject = expr(t.args(), t);
        List<Node.Case> cases = new ArrayList<>();
        List<Expr> pending = new ArrayList<>();
        while (true) {
            skipBlankText();
            if (pos >= tokens.size()) throw new LoomaException("Unclosed @switch", t.line(), null);
            Token n = tokens.get(pos++);
            if (n.kind() != Kind.DIRECTIVE) {
                throw new LoomaException("Only @case and @default are allowed directly inside @switch", n.line(), null);
            }
            switch (n.text()) {
                case "endswitch" -> {
                    return new Node.Switch(subject, cases);
                }
                case "case" -> {
                    pending.add(expr(n.args(), n));
                    List<Node> body = caseBody();
                    // Consecutive empty cases share the next body: @case(1) @case(2) ...
                    if (!isBlank(body)) {
                        cases.add(new Node.Case(List.copyOf(pending), body));
                        pending.clear();
                    }
                }
                case "default" -> cases.add(new Node.Case(List.of(), caseBody()));
                default -> throw new LoomaException("Unexpected @" + n.text() + " inside @switch", n.line(), null);
            }
        }
    }

    private List<Node> caseBody() {
        switchDepth++;
        try {
            return block(Set.of("case", "default", "endswitch"));
        } finally {
            switchDepth--;
        }
    }

    /** Parses a loop body (where @break/@continue are legal) up to {@code end}, then consumes {@code end}. */
    private List<Node> loopBlock(Set<String> end) {
        loopDepth++;
        try {
            List<Node> body = block(end);
            pos++;
            return body;
        } finally {
            loopDepth--;
        }
    }

    private Node parseFor(Token t) {
        List<String> parts = Args.split(t.args(), ';');
        if (parts.size() != 3) {
            throw new LoomaException("Expected '@for(init; condition; step)', e.g. @for($i = 0; $i < 3; $i++)",
                    t.line(), null);
        }
        Matcher init = FOR_INIT.matcher(parts.get(0));
        Matcher step = FOR_STEP.matcher(parts.get(2));
        if (!init.matches()) {
            throw new LoomaException("@for needs an initializer like '$i = 0' but found: " + parts.get(0), t.line(), null);
        }
        if (!step.matches() || !step.group(1).equals(init.group(1))) {
            throw new LoomaException("@for step must be '$" + init.group(1)
                    + "++', '--', '+= n' or '-= n' on the loop variable, but found: " + parts.get(2), t.line(), null);
        }
        String op = step.group(2);
        boolean increment = op.equals("++") || op.equals("--");
        if (increment != step.group(3).isEmpty()) {
            throw new LoomaException("Invalid @for step: " + parts.get(2), t.line(), null);
        }
        Expr amount = expr(increment ? "1" : step.group(3), t);
        return new Node.For(init.group(1), expr(init.group(2), t), expr(parts.get(1), t), amount,
                op.startsWith("-"), loopBlock(Set.of("endfor")));
    }

    private void skipBlankText() {
        while (pos < tokens.size() && tokens.get(pos).kind() == Kind.TEXT && tokens.get(pos).text().isBlank()) pos++;
    }

    private static boolean isBlank(List<Node> nodes) {
        return nodes.stream().allMatch(n -> n instanceof Node.Text t && t.value().isBlank());
    }

    private Node parseForeach(Token t, boolean forelse) {
        Matcher m = FOREACH.matcher(t.args());
        if (!m.matches()) {
            throw new LoomaException("Expected '@" + t.text() + "(items as item)'", t.line(), null);
        }
        var items = expr(m.group(1), t);
        loopDepth++;
        List<Node> body;
        try {
            body = block(forelse ? Set.of("empty", "endforelse") : Set.of("endforeach"));
        } finally {
            loopDepth--;
        }
        List<Node> empty = List.of();
        boolean hasEmpty = tokens.get(pos++).text().equals("empty"); // consumes the terminator
        if (forelse && hasEmpty) {
            empty = block(Set.of("endforelse"));
            pos++;
        }
        return new Node.Foreach(items, m.group(2), body, empty);
    }

    private Node parseSection(Token t) {
        List<String> a = Args.split(t.args());
        String name = Args.literal(a.get(0), t.line());
        if (a.size() > 1) {
            return new Node.Section(name, List.of(new Node.Echo(expr(a.get(1), t), false)));
        }
        List<Node> body = block(Set.of("endsection", "show"));
        pos++;
        return new Node.Section(name, body);
    }

    private static Expr expr(String source, Token t) {
        return Expressions.parse(source, t.line());
    }
}
