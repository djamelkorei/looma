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
            case "if" -> parseIf(t, false);
            case "unless" -> parseIf(t, true);
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

    private Node parseIf(Token t, boolean negate) {
        String end = negate ? "endunless" : "endif";
        Set<String> stops = negate ? Set.of("else", end) : Set.of("elseif", "else", end);
        List<Node.Branch> branches = new ArrayList<>();
        List<Node> orElse = List.of();
        String cond = negate ? "!(" + t.args() + ")" : t.args();
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
