package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.internal.Lexer.Kind;
import io.github.djamelkorei.looma.internal.Lexer.Token;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Turns lexer tokens into nodes. The parser itself knows only text, echoes and components; every
 * {@code @directive} is delegated to the {@link Directive} registered under its name.
 */
public final class Parser {

    /** Result of parsing one template. */
    public record Result(List<Node> nodes, Expr layout) {}

    private static final String COMPONENT_END = "</x>";

    private final List<Token> tokens;
    private final DirectiveRegistry registry;
    private final Map<String, Integer> markers = new HashMap<>();
    private int pos;
    private Expr layout;

    private Parser(List<Token> tokens, DirectiveRegistry registry) {
        this.tokens = tokens;
        this.registry = registry;
    }

    public static Result parse(String source, DirectiveRegistry registry) {
        Parser parser = new Parser(Lexer.lex(source, registry), registry);
        List<Node> nodes = parser.block(Set.of());
        return new Result(nodes, parser.layout);
    }

    /** Parses nodes until a tag named in {@code stops} (left unconsumed) or, if none are given, the end. */
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
                case TEXT -> nodes.add(new TextNode(t.text()));
                case ECHO -> nodes.add(new EchoNode(Expressions.parse(t.text(), t.line()), false));
                case RAW -> nodes.add(new EchoNode(Expressions.parse(t.text(), t.line()), true));
                case DIRECTIVE -> {
                    Node node = directive(t);
                    if (node != null) nodes.add(node);
                }
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
        Directive directive = registry.directive(t.text());
        if (directive == null) {
            throw new LoomaException("Unexpected @" + t.text(), t.line(), null);
        }
        return directive.parse(new Call(t));
    }

    // ---- components

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
        List<ComponentNode.Attr> attrs = parseAttrs(t);
        String name = t.text();
        if (name.equals("slot") || name.startsWith("slot:")) {
            String slotName = name.startsWith("slot:") ? name.substring(5) : attrs.stream()
                    .filter(a -> a.name().equals("name")).findFirst()
                    .map(a -> a.value().source())
                    .orElseThrow(() -> new LoomaException("<x-slot> needs a name", t.line(), null));
            return new SlotNode(slotName, body);
        }
        List<Node> main = new ArrayList<>();
        Map<String, List<Node>> slots = new LinkedHashMap<>();
        for (Node n : body) {
            if (n instanceof SlotNode s) slots.put(s.name(), s.body());
            else main.add(n);
        }
        return new ComponentNode(name, attrs, main, slots);
    }

    /** Parses {@code a="x" :b="expr" flag}; {@code :b} values are expressions. */
    private List<ComponentNode.Attr> parseAttrs(Token t) {
        String raw = t.args();
        List<ComponentNode.Attr> attrs = new ArrayList<>();
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
                attrs.add(new ComponentNode.Attr(name.substring(1),
                        Expressions.parse(value == null ? "true" : value, t.line())));
            } else if (value == null) {
                attrs.add(new ComponentNode.Attr(name, Expressions.parse("true", t.line())));
            } else {
                attrs.add(new ComponentNode.Attr(name, new Expr(
                        new org.springframework.expression.common.LiteralExpression(value), value, t.line())));
            }
        }
        return attrs;
    }

    /** The {@link ParseContext} handed to a directive for one opening tag. */
    private final class Call implements ParseContext {

        private final Token token;

        Call(Token token) {
            this.token = token;
        }

        @Override public String name() { return token.text(); }
        @Override public String args() { return token.args(); }
        @Override public int line() { return token.line(); }

        @Override
        public Expr expr() {
            if (token.args() == null) {
                throw error("@" + token.text() + " needs an argument");
            }
            return Expressions.parse(token.args(), token.line());
        }

        @Override public Expr expr(String source) { return Expressions.parse(source, token.line()); }
        @Override public Expr expr(Tag tag) { return Expressions.parse(requireArgs(tag), tag.line()); }

        private String requireArgs(Tag tag) {
            if (tag.args() == null) throw error(tag, "@" + tag.name() + " needs an argument");
            return tag.args();
        }

        @Override
        public List<String> splitArgs() {
            return split(token.args() == null ? "" : token.args(), ',');
        }

        @Override public List<String> split(String text, char delimiter) { return Args.split(text, delimiter); }
        @Override public String literal(String quoted) { return Args.literal(quoted, token.line()); }

        @Override
        public Block block(String... endTags) {
            List<Node> nodes = Parser.this.block(Set.of(endTags));
            Token end = tokens.get(pos++);
            return new Block(nodes, new Tag(end.text(), end.args(), end.line()));
        }

        @Override public Node echo(Expr expr, boolean raw) { return new EchoNode(expr, raw); }

        @Override
        public boolean isBlank(List<Node> nodes) {
            return nodes.stream().allMatch(n -> n instanceof TextNode t && t.value().isBlank());
        }

        @Override
        public <T> T within(String marker, Supplier<T> body) {
            markers.merge(marker, 1, Integer::sum);
            try {
                return body.get();
            } finally {
                markers.merge(marker, -1, Integer::sum);
            }
        }

        @Override public boolean isWithin(String marker) { return markers.getOrDefault(marker, 0) > 0; }
        @Override public void layout(Expr layout) { Parser.this.layout = layout; }
        @Override public LoomaException error(String message) { return new LoomaException(message, token.line(), null); }
        @Override public LoomaException error(Tag tag, String message) { return new LoomaException(message, tag.line(), null); }
    }
}
