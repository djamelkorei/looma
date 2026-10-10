package io.github.djamelkorei.looma.intellij.lang;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/** The directives known in a project: the built-ins plus custom ones (see {@link CustomDirectiveProvider}). */
public final class LoomaProjectDirectives {

    private LoomaProjectDirectives() {}

    public static LoomaDirectives get(@Nullable Project project) {
        if (project == null || project.isDefault() || project.isDisposed()) return LoomaDirectives.BUILTIN;
        LoomaDirectives directives = LoomaDirectives.BUILTIN;
        for (CustomDirectiveProvider provider : CustomDirectiveProvider.EP.getExtensionList()) {
            directives = directives.with(provider.directives(project));
        }
        return directives;
    }
}
