package io.github.djamelkorei.looma.intellij.java;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import io.github.djamelkorei.looma.intellij.psi.LoomaFiles;
import io.github.djamelkorei.looma.intellij.psi.LoomaPsiFile;
import io.github.djamelkorei.looma.intellij.psi.LoomaStringLiteral;
import io.github.djamelkorei.looma.intellij.psi.LoomaTemplates;
import io.github.djamelkorei.looma.intellij.reference.LoomaModelVariables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The model of a template, read from the controllers that render it: {@code model.addAttribute("user", u)},
 * {@code mav.addObject(...)}, {@code @ModelAttribute} methods and parameters, form objects, and
 * {@code engine.render("view", Map.of(...))}. Layouts and partials see the model of the templates that extend
 * or include them. Template helpers ({@code String}, {@code Math} and yours) resolve to their classes.
 */
public final class LoomaJavaModel implements LoomaModelVariables {

    /** A model attribute: where it is set, and its type if known. */
    public record Attribute(String name, PsiElement target, @Nullable PsiType type) {}

    private static final String MODEL_ATTRIBUTE = "org.springframework.web.bind.annotation.ModelAttribute";
    /** Parameter annotations that mean "not a model attribute". */
    private static final Set<String> NOT_MODEL = Set.of(
            "org.springframework.web.bind.annotation.RequestParam",
            "org.springframework.web.bind.annotation.PathVariable",
            "org.springframework.web.bind.annotation.RequestBody",
            "org.springframework.web.bind.annotation.RequestHeader",
            "org.springframework.web.bind.annotation.CookieValue",
            "org.springframework.web.bind.annotation.SessionAttribute",
            "org.springframework.web.bind.annotation.RequestAttribute",
            "org.springframework.security.core.annotation.AuthenticationPrincipal");
    private static final int MAX_DEPTH = 5;

    @Override
    public @Nullable PsiElement resolve(@NotNull PsiFile template, @NotNull String name) {
        Attribute attribute = attributes(template).get(name);
        if (attribute != null) return attribute.target();
        return LoomaJavaHelpers.helpers(template.getProject()).get(name);
    }

    @Override
    public @NotNull Collection<LookupElement> variants(@NotNull PsiFile template) {
        List<LookupElement> out = new ArrayList<>();
        for (Attribute a : attributes(template).values()) {
            out.add(LookupElementBuilder.create(a.name())
                    .withTypeText(a.type() == null ? "model" : a.type().getPresentableText(), true)
                    .withIcon(com.intellij.icons.AllIcons.Nodes.Variable));
        }
        LoomaJavaHelpers.helpers(template.getProject()).forEach((name, cls) -> out.add(LookupElementBuilder
                .create(name).withTypeText("helper " + cls.getName(), true).withIcon(com.intellij.icons.AllIcons.Nodes.Class)));
        return out;
    }

    /** The model attributes of a template, by name (cached until code changes). */
    public static @NotNull Map<String, Attribute> attributes(@NotNull PsiFile template) {
        LoomaPsiFile looma = LoomaFiles.looma(template);
        if (looma == null || DumbService.isDumb(template.getProject()) || LoomaTemplates.isComponent(looma)) {
            return Map.of();
        }
        return CachedValuesManager.getCachedValue(looma, () -> CachedValueProvider.Result.create(
                compute(looma), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static Map<String, Attribute> compute(LoomaPsiFile template) {
        Map<String, Attribute> out = new LinkedHashMap<>();
        collect(template, out, new HashSet<>(), 0);
        return out;
    }

    /** Model of {@code template}: its controllers, then the templates that extend or include it. */
    private static void collect(LoomaPsiFile template, Map<String, Attribute> out, Set<PsiFile> seen, int depth) {
        if (depth > MAX_DEPTH || !seen.add(template)) return;
        GlobalSearchScope scope = GlobalSearchScope.projectScope(template.getProject());
        List<LoomaPsiFile> users = new ArrayList<>();
        PsiFile target = template.getOriginalFile();
        for (PsiReference ref : ReferencesSearch.search(target, scope).findAll()) {
            PsiElement element = ref.getElement();
            if (element instanceof PsiLiteralExpression literal) {
                PsiMethod method = PsiTreeUtil.getParentOfType(literal, PsiMethod.class);
                if (method != null) fromMethod(method, literal, out);
            } else if (element instanceof LoomaStringLiteral literal && literal.getDirective() != null
                    && Set.of("include", "extends").contains(literal.getDirective().getName())) {
                LoomaPsiFile user = LoomaFiles.looma(literal.getContainingFile());
                if (user != null && user != template) users.add(user);
            }
        }
        for (LoomaPsiFile user : users) collect(user, out, seen, depth + 1);
    }

    private static void fromMethod(PsiMethod method, PsiLiteralExpression view, Map<String, Attribute> out) {
        fromViewCall(view, out);
        PsiCodeBlock body = method.getBody();
        if (body != null) {
            body.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    fromSetter(call, out);
                }
            });
        }
        for (PsiParameter p : method.getParameterList().getParameters()) fromParameter(p, out);
        PsiClass cls = method.getContainingClass();
        if (cls != null) {
            for (PsiMethod m : cls.getMethods()) {
                PsiAnnotation a = m.getAnnotation(MODEL_ATTRIBUTE);
                if (a == null || m.getReturnType() == null || PsiTypes.voidType().equals(m.getReturnType())) continue;
                String name = annotationName(a);
                add(out, name != null ? name : conventionName(m.getReturnType()), m, m.getReturnType());
            }
        }
    }

    /** {@code model.addAttribute("x", v)}, {@code mav.addObject("x", v)}, {@code map.put("x", v)}. */
    private static void fromSetter(PsiMethodCallExpression call, Map<String, Attribute> out) {
        String method = call.getMethodExpression().getReferenceName();
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (!"addAttribute".equals(method) && !"addObject".equals(method) && !"put".equals(method)) return;
        if ("put".equals(method)) {
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier == null || !InheritanceUtil.isInheritor(qualifier.getType(), "java.util.Map")) return;
            if (!(qualifier instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiParameter)) return;
        }
        if (args.length == 2 && args[0] instanceof PsiLiteralExpression name && name.getValue() instanceof String s) {
            add(out, s, name, args[1].getType());
        } else if (args.length == 1 && !"put".equals(method) && args[0].getType() != null) {
            add(out, conventionName(args[0].getType()), args[0], args[0].getType());
        }
    }

    /** {@code new ModelAndView("view", "x", v)} and {@code render("view", Map.of("x", v, ...))}. */
    private static void fromViewCall(PsiLiteralExpression view, Map<String, Attribute> out) {
        if (!(view.getParent() instanceof PsiExpressionList list)) return;
        PsiExpression[] args = list.getExpressions();
        if (args.length == 3 && args[1] instanceof PsiLiteralExpression name && name.getValue() instanceof String s) {
            add(out, s, name, args[2].getType());
        } else if (args.length >= 2 && PsiUtil.skipParenthesizedExprDown(args[1]) instanceof PsiMethodCallExpression map
                && "of".equals(map.getMethodExpression().getReferenceName())) {
            PsiExpression[] pairs = map.getArgumentList().getExpressions();
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                if (pairs[i] instanceof PsiLiteralExpression key && key.getValue() instanceof String s) {
                    add(out, s, key, pairs[i + 1].getType());
                }
            }
        }
    }

    /** {@code @ModelAttribute("x") Form f}, or a form object Spring binds implicitly ({@code @Valid SignupForm form}). */
    private static void fromParameter(PsiParameter p, Map<String, Attribute> out) {
        PsiAnnotation a = p.getAnnotation(MODEL_ATTRIBUTE);
        if (a != null) {
            String name = annotationName(a);
            add(out, name != null ? name : conventionName(p.getType()), p, p.getType());
            return;
        }
        for (String annotation : NOT_MODEL) if (p.hasAnnotation(annotation)) return;
        if (!(p.getType() instanceof PsiClassType type) || !(type.resolve() instanceof PsiClass cls)) return;
        PsiFile file = cls.getContainingFile();
        boolean inProject = file != null && file.getVirtualFile() != null
                && GlobalSearchScope.projectScope(p.getProject()).contains(file.getVirtualFile());
        if (inProject && !cls.isInterface() && !cls.isEnum()) add(out, conventionName(type), p, type);
    }

    private static @Nullable String annotationName(PsiAnnotation a) {
        String value = AnnotationUtil.getStringAttributeValue(a, "value");
        if (value == null || value.isEmpty()) value = AnnotationUtil.getStringAttributeValue(a, "name");
        return value == null || value.isEmpty() ? null : value;
    }

    /** Spring's default attribute name: {@code SignupForm} gives {@code signupForm}, {@code List<Post>} {@code postList}. */
    static String conventionName(PsiType type) {
        PsiType element = type;
        String suffix = "";
        if (type instanceof PsiArrayType array) {
            element = array.getComponentType();
            suffix = "List";
        } else if (InheritanceUtil.isInheritor(type, "java.util.Collection")) {
            PsiType parameter = PsiUtil.extractIterableTypeParameter(type, false);
            if (parameter != null) element = parameter;
            suffix = "List";
        }
        String simple = element instanceof PsiClassType ct ? ct.getClassName() : element.getPresentableText();
        if (simple == null || simple.isEmpty()) return "value";
        boolean keep = simple.length() > 1 && Character.isUpperCase(simple.charAt(0)) && Character.isUpperCase(simple.charAt(1));
        return (keep ? simple : Character.toLowerCase(simple.charAt(0)) + simple.substring(1)) + suffix;
    }

    private static void add(Map<String, Attribute> out, String name, PsiElement target, @Nullable PsiType type) {
        out.putIfAbsent(name, new Attribute(name, target, type));
    }
}
