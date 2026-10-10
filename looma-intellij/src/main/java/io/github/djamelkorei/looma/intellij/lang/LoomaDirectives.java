package io.github.djamelkorei.looma.intellij.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the plugin knows about directives: the built-in ones (mirroring {@code BuiltinDirectives}) plus
 * any custom ones found in the project. The lexer only needs {@link #syntax}; the parser also needs the
 * block structure.
 */
public final class LoomaDirectives {

    /** Mirrors {@code io.github.djamelkorei.looma.spi.Syntax}. */
    public enum Syntax {
        /** {@code @name}: no argument list. */
        NONE,
        /** {@code @name} or {@code @name(args)}; the '(' must directly follow the name. */
        OPTIONAL,
        /** {@code @name(args)}; blanks may precede '('. Without '(' the text is left alone. */
        REQUIRED
    }

    /**
     * One directive.
     *
     * @param middle secondary tags inside the block, e.g. {@code elseif} and {@code else}
     * @param ends   tags that close the block, e.g. {@code endif}; empty for inline directives
     * @param usage  how it is written, shown in completion and documentation
     */
    public record Directive(String name, Syntax syntax, Map<String, Syntax> middle, Map<String, Syntax> ends,
            String usage, String doc) {

        public boolean isBlock() {
            return !ends.isEmpty();
        }
    }

    /** Names handled by the lexer itself. */
    public static final String VERBATIM = "verbatim";
    public static final String END_VERBATIM = "endverbatim";

    public static final LoomaDirectives BUILTIN = new LoomaDirectives(builtins());

    private final Map<String, Directive> directives = new LinkedHashMap<>();
    private final Map<String, Syntax> syntax = new LinkedHashMap<>();

    public LoomaDirectives(Collection<Directive> all) {
        for (Directive d : all) {
            directives.putIfAbsent(d.name(), d);
            syntax.putIfAbsent(d.name(), d.syntax());
        }
        for (Directive d : all) {
            d.middle().forEach(syntax::putIfAbsent);
            d.ends().forEach(syntax::putIfAbsent);
        }
    }

    /** The built-ins plus {@code custom}; built-ins win on a name clash, as the engine would refuse it. */
    public LoomaDirectives with(Collection<Directive> custom) {
        if (custom.isEmpty()) return this;
        List<Directive> all = new ArrayList<>(directives.values());
        all.addAll(custom);
        return new LoomaDirectives(all);
    }

    /**
     * How {@code @name} is written, or null if the name is not a directive or tag. {@code @endverbatim} is
     * null: the lexer consumes it together with {@code @verbatim}, and a stray one is plain text.
     */
    public Syntax syntax(String name) {
        return name.equals(END_VERBATIM) ? null : syntax.get(name);
    }

    /** The directive that opens with this name, or null (also null for tags such as {@code endif}). */
    public Directive directive(String name) {
        return directives.get(name);
    }

    public Collection<Directive> all() {
        return Collections.unmodifiableCollection(directives.values());
    }

    /** Every name usable after '@', including secondary tags. */
    public Collection<String> names() {
        return Collections.unmodifiableCollection(syntax.keySet());
    }

    private static List<Directive> builtins() {
        List<Directive> d = new ArrayList<>();
        // conditionals
        d.add(block("if", Syntax.REQUIRED, Map.of("elseif", Syntax.REQUIRED, "else", Syntax.NONE), "endif",
                "@if(condition) ... @elseif(condition) ... @else ... @endif",
                "Renders the first branch whose condition is truthy. PHP-style truthiness: null, 0, \"\" and"
                        + " empty collections are false."));
        d.add(block("unless", Syntax.REQUIRED, Map.of("else", Syntax.NONE), "endunless",
                "@unless(condition) ... @endunless", "Renders the body when the condition is falsy."));
        d.add(block("isset", Syntax.REQUIRED, Map.of("else", Syntax.NONE), "endisset",
                "@isset(value) ... @endisset", "Renders the body when the value is not null."));
        d.add(block("auth", Syntax.OPTIONAL, Map.of("else", Syntax.NONE), "endauth",
                "@auth ... @endauth, @auth('ADMIN', 'EDITOR')",
                "Renders the body when <code>principal</code> is set and, with roles, the request is in one of"
                        + " them (<code>request.isUserInRole</code>)."));
        d.add(block("guest", Syntax.NONE, Map.of("else", Syntax.NONE), "endguest",
                "@guest ... @endguest", "Renders the body when nobody is signed in (no <code>principal</code>)."));
        d.add(block("switch", Syntax.REQUIRED, Map.of("case", Syntax.REQUIRED, "default", Syntax.NONE),
                "endswitch", "@switch($x) @case(1) ... @break @default ... @endswitch",
                "Renders the matching <code>@case</code>; stacked cases share a body, <code>@break</code> ends"
                        + " a case."));
        // loops
        d.add(block("foreach", Syntax.REQUIRED, Map.of(), "endforeach", "@foreach($items as $item) ... @endforeach",
                "Loops over a collection, array or map (map entries have <code>key</code> and"
                        + " <code>value</code>). Inside, <code>$loop</code> has index, iteration, count, first,"
                        + " last, odd, even, remaining, parent and depth."));
        d.add(block("forelse", Syntax.REQUIRED, Map.of("empty", Syntax.NONE), "endforelse",
                "@forelse($items as $item) ... @empty ... @endforelse",
                "Like <code>@foreach</code>, with an <code>@empty</code> branch for empty or null collections."));
        d.add(block("for", Syntax.REQUIRED, Map.of(), "endfor", "@for($i = 0; $i < 10; $i++) ... @endfor",
                "Counted loop. The step is <code>++</code>, <code>--</code>, <code>+= n</code> or"
                        + " <code>-= n</code> on the loop variable."));
        d.add(block("while", Syntax.REQUIRED, Map.of(), "endwhile", "@while(condition) ... @endwhile",
                "Loops while the condition is truthy; the body must change it."));
        d.add(inline("break", Syntax.OPTIONAL, "@break, @break(condition)",
                "Leaves the loop (or ends a <code>@switch</code> case), optionally only when the condition is"
                        + " truthy."));
        d.add(inline("continue", Syntax.OPTIONAL, "@continue, @continue(condition)",
                "Skips to the next iteration, optionally only when the condition is truthy."));
        // composition
        d.add(inline("include", Syntax.REQUIRED, "@include('partials.nav', {'key': value})",
                "Renders another template with the current scope plus the given variables."));
        d.add(inline("extends", Syntax.REQUIRED, "@extends('layouts.app')",
                "Renders this template inside a layout; its <code>@section</code>s fill the layout's"
                        + " <code>@yield</code>s."));
        d.add(new Directive("section", Syntax.REQUIRED, Map.of(),
                Map.of("endsection", Syntax.NONE, "show", Syntax.NONE),
                "@section('name') ... @endsection, @section('name', 'value')",
                "Defines a section for the layout. <code>@show</code> instead of <code>@endsection</code> also"
                        + " outputs it in place."));
        d.add(inline("yield", Syntax.REQUIRED, "@yield('name', 'default')",
                "Outputs a section defined by a child template, or the default."));
        d.add(inline("parent", Syntax.NONE, "@parent", "Inside a section, outputs the parent layout's section."));
        d.add(block("push", Syntax.REQUIRED, Map.of(), "endpush", "@push('scripts') ... @endpush",
                "Appends the body to a stack."));
        d.add(block("prepend", Syntax.REQUIRED, Map.of(), "endprepend", "@prepend('scripts') ... @endprepend",
                "Prepends the body to a stack."));
        d.add(inline("stack", Syntax.REQUIRED, "@stack('scripts')",
                "Outputs a stack; works even if the pushes come later in the page."));
        d.add(inline("class", Syntax.REQUIRED, "@class('btn', {'active': $on})",
                "Builds a <code>class=\"...\"</code> attribute: strings always, map keys when truthy."));
        d.add(inline("style", Syntax.REQUIRED, "@style('margin: 0', {'color: red': $err})",
                "Builds a <code>style=\"...\"</code> attribute: strings always, map keys when truthy."));
        d.add(block("once", Syntax.NONE, Map.of(), "endonce", "@once ... @endonce",
                "Renders the body only the first time per page."));
        d.add(inline("json", Syntax.REQUIRED, "@json($data)",
                "Outputs JSON that is safe inside <code>&lt;script&gt;</code>."));
        // forms and i18n
        d.add(inline("csrf", Syntax.NONE, "@csrf", "Hidden input with the CSRF token."));
        d.add(inline("method", Syntax.REQUIRED, "@method('PUT')", "Hidden <code>_method</code> input."));
        d.add(block("error", Syntax.REQUIRED, Map.of(), "enderror", "@error('field') {{ $message }} @enderror",
                "Renders the body when the field has a validation error; <code>$message</code> is the first"
                        + " error, <code>$messages</code> all of them."));
        d.add(inline("lang", Syntax.REQUIRED, "@lang('key', args...)",
                "Outputs a localized, escaped message from your <code>MessageSource</code>."));
        d.add(new Directive(VERBATIM, Syntax.NONE, Map.of(), Map.of(END_VERBATIM, Syntax.NONE),
                "@verbatim ... @endverbatim", "The body is output as is, without any processing."));
        return d;
    }

    private static Directive block(String name, Syntax syntax, Map<String, Syntax> middle, String end,
            String usage, String doc) {
        return new Directive(name, syntax, middle, Map.of(end, Syntax.NONE), usage, doc);
    }

    private static Directive inline(String name, Syntax syntax, String usage, String doc) {
        return new Directive(name, syntax, Map.of(), Map.of(), usage, doc);
    }
}
