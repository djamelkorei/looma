package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.LoomaException;
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

    private static final Pattern FOREACH = Pattern.compile("(?s)^(.*\\S)\\s+as\\s+\\$?(\\w+)$");

    private final List<Token> tokens;
    private int pos;

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
            pos++;
            switch (t.kind()) {
                case TEXT -> nodes.add(new Node.Text(t.text()));
                case ECHO -> nodes.add(new Node.Echo(Expressions.parse(t.text(), t.line()), false));
                case RAW -> nodes.add(new Node.Echo(Expressions.parse(t.text(), t.line()), true));
                case DIRECTIVE -> nodes.add(directive(t));
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

    private Node parseSwitch(Token t) {
        var subject = expr(t.args(), t);
        List<Node.Case> cases = new ArrayList<>();
        List<org.springframework.expression.Expression> pending = new ArrayList<>();
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
        List<Node> body = block(Set.of("case", "default", "break", "endswitch"));
        if (tokens.get(pos).text().equals("break")) pos++;
        return body;
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
        List<Node> body = block(forelse ? Set.of("empty", "endforelse") : Set.of("endforeach"));
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

    private static org.springframework.expression.Expression expr(String source, Token t) {
        return Expressions.parse(source, t.line());
    }
}
