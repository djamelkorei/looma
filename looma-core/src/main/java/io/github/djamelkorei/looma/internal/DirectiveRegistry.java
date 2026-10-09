package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Directives by name, plus the syntax of every tag the lexer must recognize. */
public final class DirectiveRegistry {

    /** Names handled by the lexer itself and therefore unavailable to directives. */
    private static final Set<String> RESERVED = Set.of("verbatim", "endverbatim");

    private final Map<String, Directive> directives = new HashMap<>();
    private final Map<String, Syntax> syntax = new HashMap<>();

    public DirectiveRegistry(Collection<? extends Directive> all) {
        for (Directive d : all) {
            add(d.name(), d.syntax(), d);
        }
        for (Directive d : all) {
            d.tags().forEach((tag, tagSyntax) -> add(tag, tagSyntax, null));
        }
    }

    private void add(String name, Syntax tagSyntax, Directive owner) {
        if (!name.matches("[A-Za-z]+")) {
            throw new IllegalArgumentException("Directive/tag names must be letters only: '" + name + "'");
        }
        if (RESERVED.contains(name)) {
            throw new IllegalArgumentException("'" + name + "' is reserved by the engine");
        }
        if (owner != null && directives.putIfAbsent(name, owner) != null) {
            throw new IllegalArgumentException("Directive '@" + name + "' is registered twice");
        }
        Syntax previous = syntax.putIfAbsent(name, tagSyntax);
        if (previous != null && (previous != tagSyntax || owner != null)) {
            // The same secondary tag (e.g. 'else') may be shared by several directives if written alike.
            throw new IllegalArgumentException("Tag '@" + name + "' is defined twice with different meaning");
        }
    }

    /** The directive that starts with this name, or null (also null for secondary tags such as 'endif'). */
    public Directive directive(String name) {
        return directives.get(name);
    }

    /** How {@code @name} is written, or null if the name is not a known directive or tag. */
    public Syntax syntax(String name) {
        return syntax.get(name);
    }
}
