package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import java.util.List;

/** Every directive that ships with Looma. This list is the whole feature set of the engine core. */
public final class BuiltinDirectives {

    private BuiltinDirectives() {}

    public static List<Directive> all() {
        return List.of(
                // conditionals
                ConditionalDirective.ifDirective(),
                ConditionalDirective.unless(),
                ConditionalDirective.isset(),
                ConditionalDirective.auth(),
                ConditionalDirective.guest(),
                new SwitchDirective(),
                // loops
                ForeachDirective.foreach(),
                ForeachDirective.forelse(),
                new ForDirective(),
                new WhileDirective(),
                LoopControlDirective.breakDirective(),
                LoopControlDirective.continueDirective(),
                // composition
                new IncludeDirective(),
                LayoutDirectives.extendsDirective(),
                LayoutDirectives.section(),
                LayoutDirectives.yield(),
                LayoutDirectives.parent(),
                StackDirectives.push(),
                StackDirectives.prepend(),
                StackDirectives.stack(),
                new OnceDirective(),
                new JsonDirective(),
                // forms and i18n
                FormDirectives.csrf(),
                FormDirectives.method(),
                new LangDirective());
    }
}
