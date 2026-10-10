package io.github.djamelkorei.looma.intellij.structure;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.impl.TemplateLanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The structure of a template: its blocks and directives, next to the HTML structure. */
public final class LoomaStructureViewFactory implements PsiStructureViewFactory {

    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        return TemplateLanguageStructureViewBuilder.create(psiFile, LoomaStructureViewModel::new);
    }
}
