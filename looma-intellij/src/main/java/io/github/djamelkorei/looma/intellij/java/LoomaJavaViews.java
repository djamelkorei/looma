package io.github.djamelkorei.looma.intellij.java;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Where Java code names a view: controller return values, {@code ModelAndView}s and {@code LoomaEngine} calls. */
public final class LoomaJavaViews {

    public static final String CONTROLLER = "org.springframework.stereotype.Controller";
    public static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    public static final String MODEL_AND_VIEW = "org.springframework.web.servlet.ModelAndView";
    public static final String ENGINE = "io.github.djamelkorei.looma.LoomaEngine";
    private static final Set<String> ENGINE_METHODS = Set.of("render", "template", "exists");

    private LoomaJavaViews() {}

    /** The view name if {@code literal} is one (prefixes such as {@code redirect:} excluded), else null. */
    public static @Nullable String viewName(@NotNull PsiLiteralExpression literal) {
        if (!(literal.getValue() instanceof String value) || value.isEmpty() || value.contains(":")) return null;
        return isViewPosition(literal) ? value : null;
    }

    private static boolean isViewPosition(PsiLiteralExpression literal) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(literal.getParent());
        if (parent instanceof PsiReturnStatement) {
            PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, true, PsiLambdaExpression.class);
            return method != null && isController(method.getContainingClass());
        }
        if (!(parent instanceof PsiExpressionList args) || args.getExpressions().length == 0
                || args.getExpressions()[0] != literal) {
            return false;
        }
        PsiElement call = args.getParent();
        if (call instanceof PsiNewExpression newExpression) {
            PsiJavaCodeReferenceElement ref = newExpression.getClassReference();
            return ref != null && isClass(ref, MODEL_AND_VIEW);
        }
        if (call instanceof PsiMethodCallExpression methodCall) {
            String name = methodCall.getMethodExpression().getReferenceName();
            if ("setViewName".equals(name)) return true;
            if (ENGINE_METHODS.contains(name)) {
                PsiMethod method = methodCall.resolveMethod();
                PsiClass owner = method == null ? null : method.getContainingClass();
                return owner != null && ENGINE.equals(owner.getQualifiedName());
            }
        }
        return false;
    }

    /** A Spring MVC controller rendering views (not a {@code @RestController}). */
    public static boolean isController(@Nullable PsiClass cls) {
        return cls != null && AnnotationUtil.isAnnotated(cls, CONTROLLER, 0)
                && !AnnotationUtil.isAnnotated(cls, REST_CONTROLLER, 0);
    }

    private static boolean isClass(PsiJavaCodeReferenceElement ref, String qualifiedName) {
        if (ref.resolve() instanceof PsiClass cls) return qualifiedName.equals(cls.getQualifiedName());
        String text = ref.getQualifiedName();
        return qualifiedName.equals(text) || qualifiedName.endsWith("." + text);
    }
}
