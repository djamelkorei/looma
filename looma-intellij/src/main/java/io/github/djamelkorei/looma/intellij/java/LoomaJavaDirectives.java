package io.github.djamelkorei.looma.intellij.java;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import io.github.djamelkorei.looma.intellij.lang.CustomDirectiveProvider;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives.Syntax;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom directives of the project: classes implementing {@code Directive} whose {@code name()},
 * {@code syntax()} and {@code tags()} return constants, e.g. {@code return "money";}.
 */
public final class LoomaJavaDirectives implements CustomDirectiveProvider {

    public static final String SPI = "io.github.djamelkorei.looma.spi.Directive";

    /**
     * Finding the classes may index templates, which creates a lexer, which asks for the directives again:
     * such nested calls get none.
     */
    private static final ThreadLocal<Boolean> COMPUTING = ThreadLocal.withInitial(() -> false);

    @Override
    public Collection<LoomaDirectives.Directive> directives(Project project) {
        if (DumbService.isDumb(project) || COMPUTING.get()) return List.of();
        COMPUTING.set(true);
        try {
            return CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(
                    compute(project), PsiModificationTracker.getInstance(project).forLanguage(JavaLanguage.INSTANCE)));
        } finally {
            COMPUTING.set(false);
        }
    }

    private static List<LoomaDirectives.Directive> compute(Project project) {
        List<LoomaDirectives.Directive> out = new ArrayList<>();
        PsiClass spi = JavaPsiFacade.getInstance(project).findClass(SPI, GlobalSearchScope.allScope(project));
        if (spi == null) return out;
        for (PsiClass cls : ClassInheritorsSearch.search(spi, GlobalSearchScope.projectScope(project), true).findAll()) {
            String name = returnedString(cls, "name");
            if (name == null || !name.matches("[A-Za-z]+")) continue;
            Syntax syntax = syntax(returned(cls, "syntax"));
            Map<String, Syntax> middle = new LinkedHashMap<>();
            Map<String, Syntax> ends = new LinkedHashMap<>();
            tags(returned(cls, "tags")).forEach((tag, s) -> (tag.startsWith("end") ? ends : middle).put(tag, s));
            String usage = "@" + name + (syntax == Syntax.NONE ? "" : "(...)") + (ends.isEmpty() ? "" : " ... @" + ends.keySet().iterator().next());
            String owner = cls.getQualifiedName() != null ? cls.getQualifiedName() : cls.getText().lines().findFirst().orElse("");
            out.add(new LoomaDirectives.Directive(name, syntax == null ? Syntax.REQUIRED : syntax, middle, ends, usage,
                    "Custom directive defined in <code>" + owner + "</code>."));
        }
        return out;
    }

    /** The string constant a no-argument method of {@code cls} returns, or null. */
    static @Nullable String returnedString(PsiClass cls, String method) {
        return returned(cls, method) instanceof PsiLiteralExpression literal && literal.getValue() instanceof String s ? s : null;
    }

    /** The expression of the single {@code return} of a no-argument method, or null. */
    static @Nullable PsiExpression returned(PsiClass cls, String method) {
        for (PsiMethod m : cls.findMethodsByName(method, false)) {
            if (m.getParameterList().isEmpty() && m.getBody() != null) {
                PsiStatement[] statements = m.getBody().getStatements();
                if (statements.length == 1 && statements[0] instanceof PsiReturnStatement ret) {
                    return PsiUtil.skipParenthesizedExprDown(ret.getReturnValue());
                }
            }
        }
        return null;
    }

    private static @Nullable Syntax syntax(@Nullable PsiExpression e) {
        if (!(e instanceof PsiReferenceExpression ref)) return null;
        try {
            return Syntax.valueOf(ref.getReferenceName());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    /** {@code Map.of("endrepeat", Syntax.NONE, ...)}. */
    private static Map<String, Syntax> tags(@Nullable PsiExpression e) {
        Map<String, Syntax> out = new LinkedHashMap<>();
        if (!(e instanceof PsiMethodCallExpression call) || !"of".equals(call.getMethodExpression().getReferenceName())) {
            return out;
        }
        PsiExpression[] args = call.getArgumentList().getExpressions();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i] instanceof PsiLiteralExpression l && l.getValue() instanceof String tag && tag.matches("[A-Za-z]+")) {
                Syntax s = syntax(args[i + 1]);
                out.put(tag, s == null ? Syntax.NONE : s);
            }
        }
        return out;
    }
}
