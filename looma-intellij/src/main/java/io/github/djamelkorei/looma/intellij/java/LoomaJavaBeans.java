package io.github.djamelkorei.looma.intellij.java;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Beans templates may call as {@code @name.method(...)}: classes marked {@code @TemplateAccessible}, named
 * like Spring names them ({@code @Service("x")}, else the class name decapitalized). Beans listed in
 * {@code looma.beans.allowed} are not known here.
 */
public final class LoomaJavaBeans {

    public static final String ACCESSIBLE = "io.github.djamelkorei.looma.spi.TemplateAccessible";
    private static final List<String> STEREOTYPES = List.of("org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service", "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller", "jakarta.inject.Named");

    private LoomaJavaBeans() {}

    public static @NotNull Map<String, PsiClass> beans(@NotNull Project project) {
        if (DumbService.isDumb(project)) return Map.of();
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(
                compute(project), PsiModificationTracker.getInstance(project).forLanguage(JavaLanguage.INSTANCE)));
    }

    private static Map<String, PsiClass> compute(Project project) {
        Map<String, PsiClass> out = new LinkedHashMap<>();
        PsiClass annotation = JavaPsiFacade.getInstance(project).findClass(ACCESSIBLE, GlobalSearchScope.allScope(project));
        if (annotation == null) return out;
        for (PsiClass cls : AnnotatedElementsSearch.searchPsiClasses(annotation, GlobalSearchScope.projectScope(project)).findAll()) {
            String name = null;
            for (String stereotype : STEREOTYPES) {
                PsiAnnotation a = cls.getAnnotation(stereotype);
                String value = a == null ? null : AnnotationUtil.getStringAttributeValue(a, "value");
                if (value != null && !value.isEmpty()) name = value;
            }
            if (name == null && cls.getName() != null) name = LoomaJavaModel.decapitalize(cls.getName());
            if (name != null) out.putIfAbsent(name, cls);
        }
        return out;
    }
}
