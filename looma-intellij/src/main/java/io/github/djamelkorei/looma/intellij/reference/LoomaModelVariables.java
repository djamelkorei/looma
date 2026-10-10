package io.github.djamelkorei.looma.intellij.reference;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Knows the variables an application passes to a template, e.g. {@code model.addAttribute("user", ...)}. */
public interface LoomaModelVariables {

    ExtensionPointName<LoomaModelVariables> EP =
            ExtensionPointName.create("io.github.djamelkorei.looma.modelVariables");

    /** Where {@code name} is put into the model for {@code template}, or null. */
    @Nullable PsiElement resolve(@NotNull PsiFile template, @NotNull String name);

    /** Completion items for the model variables of {@code template}. */
    @NotNull Collection<LookupElement> variants(@NotNull PsiFile template);
}
