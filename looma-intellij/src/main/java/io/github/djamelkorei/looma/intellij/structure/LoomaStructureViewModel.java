package io.github.djamelkorei.looma.intellij.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import io.github.djamelkorei.looma.intellij.psi.LoomaBlock;
import io.github.djamelkorei.looma.intellij.psi.LoomaDirective;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class LoomaStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {

    LoomaStructureViewModel(@NotNull PsiFile file, @Nullable Editor editor) {
        super(file, editor, new LoomaStructureElement(file));
        withSuitableClasses(LoomaBlock.class, LoomaDirective.class);
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        return element.getValue() instanceof LoomaDirective;
    }
}
