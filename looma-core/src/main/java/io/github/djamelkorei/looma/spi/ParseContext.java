package io.github.djamelkorei.looma.spi;

import io.github.djamelkorei.looma.LoomaException;
import java.util.List;
import java.util.function.Supplier;

/** What a {@link Directive} may do while it is being parsed. */
public interface ParseContext {

    /** A tag that ended a block: its name, raw argument text (null if none) and template line. */
    record Tag(String name, String args, int line) {}

    /** The nodes of a body together with the tag that ended it. */
    record Block(List<Node> nodes, Tag end) {}

    /** Name of the directive being parsed. */
    String name();

    /** Raw text between the parentheses of the opening tag, or null if there were none. */
    String args();

    /** Template line (1-based) of the opening tag. */
    int line();

    /** The argument text as a single expression. */
    Expr expr();

    /** Parses {@code source} as an expression located at this directive's line. */
    Expr expr(String source);

    /** Parses the arguments of a secondary tag such as {@code @elseif(...)}. */
    Expr expr(Tag tag);

    /** Splits the arguments on top-level commas, ignoring commas in strings, brackets and parentheses. */
    List<String> splitArgs();

    /** Like {@link #splitArgs()} with a custom delimiter, e.g. ';'. */
    List<String> split(String text, char delimiter);

    /** Unquotes a string literal like {@code 'name'}; fails if it is not one. */
    String literal(String quoted);

    /**
     * Parses a body until one of the given tags and consumes that tag.
     *
     * @throws LoomaException if the template ends first
     */
    Block block(String... endTags);

    /** A node that writes the expression's value, escaped unless {@code raw}. */
    Node echo(Expr expr, boolean raw);

    /** True if all nodes are whitespace-only text. */
    boolean isBlank(List<Node> nodes);

    /**
     * Runs {@code body} (typically a call to {@link #block}) with a named marker active. Directives
     * such as {@code @break} use {@link #isWithin} to check they are in a place where they make sense.
     */
    <T> T within(String marker, Supplier<T> body);

    boolean isWithin(String marker);

    /** Declares the parent layout of this template ({@code @extends}). */
    void layout(Expr layout);

    /** An exception pointing at this directive's line. */
    LoomaException error(String message);

    /** An exception pointing at the line of {@code tag}. */
    LoomaException error(Tag tag, String message);
}
