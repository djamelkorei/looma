package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Which variables a template position can see, from the directives around it. */
public final class LoomaScopes {

    public static final Set<String> LOOPS = Set.of("foreach", "forelse", "for", "while");

    /** Properties of {@code $loop}. */
    public static final Map<String, String> LOOP_PROPERTIES = properties(
            "index", "0-based index", "iteration", "1-based index", "count", "number of items",
            "first", "true on the first iteration", "last", "true on the last iteration",
            "odd", "true on odd iterations", "even", "true on even iterations",
            "remaining", "iterations left", "parent", "$loop of the enclosing loop", "depth", "nesting level, from 1");

    /** Methods of {@code $attributes} in a component. */
    public static final Map<String, String> ATTRIBUTE_METHODS = properties(
            "except", "except('a, b'): all attributes but these", "only", "only('a, b'): just these attributes",
            "merge", "merge({'class': 'x'}): add defaults; classes are appended", "get", "get('name'): one value",
            "has", "has('name'): true if given");

    /** Variables always available, with a description; the Spring starter adds the last ones to every view. */
    public static final Map<String, String> GLOBALS = properties(
            "errors", "validation errors by field (Map<String, List<String>>)",
            "request", "the HttpServletRequest", "principal", "the signed-in user, or null",
            "_csrf", "Spring Security's CSRF token",
            "String", "template helper: format, join, valueOf, isEmpty, isBlank",
            "Math", "template helper: max, min, abs, round, ceil, floor, pow, sqrt");

    /** The built-in template helpers and their methods ({@code String.format(...)}). */
    public static final Map<String, Map<String, String>> HELPERS = Map.of(
            "String", properties(
                    "format", "format(fmt, args...)", "formatLocale", "formatLocale('fr', fmt, args...)",
                    "join", "join(', ', list) or join('-', a, b...)", "valueOf", "valueOf(x)",
                    "isEmpty", "isEmpty(s), null-safe", "isBlank", "isBlank(s), null-safe"),
            "Math", properties(
                    "max", "max(a, b)", "min", "min(a, b)", "abs", "abs(a)", "round", "round(a): whole number",
                    "ceil", "ceil(a): whole number", "floor", "floor(a): whole number", "pow", "pow(base, exponent)",
                    "sqrt", "sqrt(a)"));

    /** Variables of a component template. */
    public static final Map<String, String> COMPONENT = properties(
            "slot", "the component body", "attributes", "attributes not used as variables");

    private LoomaScopes() {}

    /** The declaration or directive that defines {@code name} at {@code place}, or null. */
    public static @Nullable PsiElement resolve(@NotNull PsiElement place, @NotNull String name) {
        for (LoomaBlock block = enclosingBlock(place); block != null; block = enclosingBlock(block)) {
            LoomaDirective opener = block.getOpener();
            boolean inOpener = PsiTreeUtil.isAncestor(opener, place, false);
            if (inOpener && !opener.getName().equals("for")) continue; // @foreach($posts as $post): $posts is outside
            for (LoomaVariableDeclaration d : PsiTreeUtil.findChildrenOfType(opener, LoomaVariableDeclaration.class)) {
                if (d.getName().equals(name)) return d;
            }
            if (inOpener) continue;
            if (name.equals("loop") && LOOPS.contains(opener.getName())) return opener;
            if ((name.equals("message") || name.equals("messages")) && opener.getName().equals("error")) return opener;
        }
        return null;
    }

    /** Every variable visible at {@code place} with a short description, innermost first. */
    public static @NotNull Map<String, String> visible(@NotNull PsiElement place) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (LoomaBlock block = enclosingBlock(place); block != null; block = enclosingBlock(block)) {
            LoomaDirective opener = block.getOpener();
            boolean inOpener = PsiTreeUtil.isAncestor(opener, place, false);
            if (inOpener && !opener.getName().equals("for")) continue;
            for (LoomaVariableDeclaration d : PsiTreeUtil.findChildrenOfType(opener, LoomaVariableDeclaration.class)) {
                vars.putIfAbsent(d.getName(), "@" + opener.getName() + " variable");
            }
            if (inOpener) continue;
            if (LOOPS.contains(opener.getName())) vars.putIfAbsent("loop", "loop information");
            if (opener.getName().equals("error")) {
                vars.putIfAbsent("message", "first error of the field");
                vars.putIfAbsent("messages", "all errors of the field");
            }
        }
        PsiFile file = place.getContainingFile();
        if (file != null && LoomaTemplates.isComponent(file)) COMPONENT.forEach(vars::putIfAbsent);
        GLOBALS.forEach(vars::putIfAbsent);
        return vars;
    }

    private static LoomaBlock enclosingBlock(PsiElement e) {
        return PsiTreeUtil.getParentOfType(e, LoomaBlock.class, true);
    }

    private static Map<String, String> properties(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }
}
