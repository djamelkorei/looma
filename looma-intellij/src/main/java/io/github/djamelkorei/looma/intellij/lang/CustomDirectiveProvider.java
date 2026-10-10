package io.github.djamelkorei.looma.intellij.lang;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Collection;

/** Supplies directives registered by the application, e.g. found in its Java sources. */
public interface CustomDirectiveProvider {

    ExtensionPointName<CustomDirectiveProvider> EP =
            ExtensionPointName.create("io.github.djamelkorei.looma.customDirectiveProvider");

    /** Must be cheap (cached) and must not fail in dumb mode. */
    Collection<LoomaDirectives.Directive> directives(Project project);
}
