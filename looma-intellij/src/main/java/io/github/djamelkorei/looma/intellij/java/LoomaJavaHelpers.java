package io.github.djamelkorei.looma.intellij.java;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Template helpers ({@code TemplateHelper} implementations) by the name templates use. */
public final class LoomaJavaHelpers {

    public static final String SPI = "io.github.djamelkorei.looma.spi.TemplateHelper";
    private static final Map<String, String> BUILTIN = Map.of(
            "io.github.djamelkorei.looma.helper.StringHelper", "String",
            "io.github.djamelkorei.looma.helper.MathHelper", "Math");

    private LoomaJavaHelpers() {}

    public static @NotNull Map<String, PsiClass> helpers(@NotNull Project project) {
        if (DumbService.isDumb(project)) return Map.of();
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(
                compute(project), PsiModificationTracker.getInstance(project).forLanguage(com.intellij.lang.java.JavaLanguage.INSTANCE)));
    }


    private static Map<String, PsiClass> compute(Project project) {
        Map<String, PsiClass> out = new LinkedHashMap<>();
        GlobalSearchScope all = GlobalSearchScope.allScope(project);
        PsiClass spi = JavaPsiFacade.getInstance(project).findClass(SPI, all);
        if (spi == null) return out;
        for (PsiClass cls : ClassInheritorsSearch.search(spi, all, true).findAll()) {
            String name = BUILTIN.get(cls.getQualifiedName());
            if (name == null) name = LoomaJavaDirectives.returnedString(cls, "name");
            if (name != null && !cls.isInterface() && !cls.hasModifierProperty(PsiModifier.ABSTRACT)) {
                out.put(name, cls); // a project helper of the same name replaces a built-in one
            }
        }
        return out;
    }

    public static @Nullable PsiClass helper(@NotNull Project project, @NotNull String name) {
        return helpers(project).get(name);
    }
}
