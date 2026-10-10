package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.Template;
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
    public record Result(List<Node> nodes, Expr layout, List<Template.Reference> references) {}

    private static final String COMPONENT_END = "</x>";
    /** Maximum depth of nested blocks (@if inside @foreach inside a component...). */
    static final int MAX_NESTING = 200;

    private final List<Token> tokens;
    private final DirectiveRegistry registry;
    private final Map<String, Integer> markers = new HashMap<>();
    private int pos;
    private int depth;
    /** Opening tags of the blocks currently being parsed, innermost first (for "never closed" errors). */
    private final java.util.ArrayDeque<Token> open = new java.util.ArrayDeque<>();
    private String templateName;
    private Expr layout;
    private final List<Template.Reference> references = new ArrayList<>();

    private Parser(List<Token> tokens, DirectiveRegistry registry) {
        this.tokens = tokens;
        this.registry = registry;
    }

    public static Result parse(String source, DirectiveRegistry registry) {
        return parse(source, registry, null);
    }

    /** @param templateName recorded in every expression, so evaluation errors name this template */
    public static Result parse(String source, DirectiveRegistry registry, String templateName) {
        Parser parser = new Parser(Lexer.lex(source, registry), registry);
        parser.templateName = templateName;
        List<Node> nodes = parser.block(Set.of(), null);
        return new Result(nodes, parser.layout, List.copyOf(parser.references));
    }

    /** Parses nodes until a tag named in {@code stops} (left unconsumed) or, if none are given, the end. */
    private List<Node> block(Set<String> stops, Token opener) {
        if (opener != null && ++depth > MAX_NESTING) {
            // Deeper nesting would overflow the parser's stack; fail with a message that points at the line.
            throw new LoomaException("Blocks are nested more than " + MAX_NESTING + " levels deep", opener.line(), null);
        }
        if (opener != null) open.push(opener);
        try {
            return parseBlock(stops, opener);
        } finally {
            if (opener != null) {
                depth--;
                open.pop();
            }
        }
    }

    private List<Node> parseBlock(Set<String> stops, Token opener) {
        List<Node> nodes = new ArrayList<>();
        while (pos < tokens.size()) {
            Token t = tokens.get(pos);
            if (t.kind() == Kind.DIRECTIVE && stops.contains(t.text())
                    && !(t.args() != null && registry.isDirectiveAndTag(t.text()))) {
                // A name that is both (like @empty): with arguments it is the directive, bare it is the tag.
                return nodes;
            }
            if (t.kind() == Kind.COMPONENT_CLOSE && stops.contains(COMPONENT_END)) {
                return nodes;
            }
            pos++;
            switch (t.kind()) {
                case TEXT -> nodes.add(new TextNode(t.text()));
                case ECHO -> nodes.add(new EchoNode(Expressions.parse(t.text(), t.line(), templateName), false));
                case RAW -> nodes.add(new EchoNode(Expressions.parse(t.text(), t.line(), templateName), true));
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
            // Point at the opening tag: that is where the missing end belongs, not the end of the file.
            String what = opener.kind() == Kind.DIRECTIVE ? "@" + opener.text() : "<x-" + opener.text() + ">";
            String needs = stops.contains(COMPONENT_END) ? "</x-" + opener.text().split(":")[0] + ">" : "one of " + new java.util.TreeSet<>(stops);
            throw new LoomaException("Unclosed block: " + what + " is never closed, expected " + needs,
                    opener.line(), null);
        }
        return nodes;
    }

    private Node directive(Token t) {
        Directive directive = registry.directive(t.text());
        if (directive == null) {
            // A closing tag of another block (e.g. @endsection while an @foreach is still open) means the inner block
            // was never closed: point at that block's opening line, which is where the fix belongs.
            Token inner = open.peek();
            if (inner != null && registry.syntax(t.text()) != null) {
                String what = inner.kind() == Kind.DIRECTIVE ? "@" + inner.text() : "<x-" + inner.text() + ">";
                throw new LoomaException("Unclosed block: " + what + " is never closed (found @" + t.text()
                        + " at line " + t.line() + ")", inner.line(), null);
            }
            throw new LoomaException("Unexpected @" + t.text(), t.line(), null);
        }
        return directive.parse(new Call(t, pos - 1));
    }

    // ---- components

    private Node component(Token t, boolean selfClosing) {
        List<Node> body = List.of();
        if (!selfClosing) {
            body = block(Set.of(COMPONENT_END), t);
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
        references.add(new Template.Reference("components." + name, t.line()));
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
                        Expressions.parse(value == null ? "true" : value, t.line(), templateName)));
            } else if (value == null) {
                attrs.add(new ComponentNode.Attr(name, Expressions.parse("true", t.line(), templateName)));
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
        private final int index;

        Call(Token token, int index) {
            this.token = token;
            this.index = index;
        }

        @Override public int index() { return index; }

        @Override public String name() { return token.text(); }
        @Override public String args() { return token.args(); }
        @Override public int line() { return token.line(); }

        @Override
        public Expr expr() {
            if (token.args() == null) {
                throw error("@" + token.text() + " needs an argument");
            }
            return Expressions.parse(token.args(), token.line(), templateName);
        }

        @Override public Expr expr(String source) { return Expressions.parse(source, token.line(), templateName); }
        @Override public Expr expr(Tag tag) { return Expressions.parse(requireArgs(tag), tag.line(), templateName); }

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
        @Override public String literalOrNull(String text) { return Args.literalOrNull(text); }
        @Override public void references(String templateName) {
            references.add(new Template.Reference(templateName, token.line()));
        }

        @Override
        public Block block(String... endTags) {
            List<Node> nodes = Parser.this.block(Set.of(endTags), token);
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
