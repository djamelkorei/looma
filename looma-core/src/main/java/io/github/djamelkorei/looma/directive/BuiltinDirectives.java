package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import java.util.List;

/** Every directive that ships with Looma. This list is the whole feature set of the engine core. */
public final class BuiltinDirectives {

    private BuiltinDirectives() {}

    public static List<Directive> all() {
        List<Directive> all = new java.util.ArrayList<>(List.of(
                // conditionals
                ConditionalDirective.ifDirective(),
                ConditionalDirective.unless(),
                ConditionalDirective.isset(),
                ConditionalDirective.auth(),
                ConditionalDirective.guest(),
                ConditionalDirective.can(),
                ConditionalDirective.cannot(),
                ConditionalDirective.canany(),
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
                AttributeDirectives.classDirective(),
                AttributeDirectives.style(),
                new OnceDirective(),
                new JsonDirective(),
                // forms and i18n
                FormDirectives.csrf(),
                FormDirectives.method(),
                new ErrorDirective(),
                new LangDirective()));
        all.addAll(BooleanAttributeDirectives.all());
        all.addAll(ConditionalIncludeDirectives.all());
        return List.copyOf(all);
    }
}
