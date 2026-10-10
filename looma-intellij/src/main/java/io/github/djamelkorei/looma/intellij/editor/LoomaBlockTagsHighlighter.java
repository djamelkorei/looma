package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import io.github.djamelkorei.looma.intellij.psi.LoomaBlock;
import io.github.djamelkorei.looma.intellij.psi.LoomaDirective;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** With the caret on {@code @if}, {@code @else} or {@code @endif}, highlights all tags of that block. */
public final class LoomaBlockTagsHighlighter extends HighlightUsagesHandlerFactoryBase {

    @Override
    public @Nullable HighlightUsagesHandlerBase<?> createHighlightUsagesHandler(@NotNull Editor editor,
            @NotNull PsiFile file, @NotNull PsiElement target) {
        PsiElement token = target.getNode() != null && target.getNode().getElementType() == LoomaTokenTypes.DIRECTIVE
                ? target
                : file.getViewProvider().findElementAt(editor.getCaretModel().getOffset(), LoomaLanguage.INSTANCE);
        if (token == null || token.getNode().getElementType() != LoomaTokenTypes.DIRECTIVE) return null;
        if (!(token.getParent() instanceof LoomaDirective directive) || directive.getBlock() == null) return null;
        LoomaBlock block = directive.getBlock();
        return new HighlightUsagesHandlerBase<>(editor, file) {
            @Override public @NotNull List<PsiElement> getTargets() { return List.of(token); }

            @Override
            protected void selectTargets(@NotNull List<? extends PsiElement> targets,
                    @NotNull Consumer<? super List<? extends PsiElement>> selectionConsumer) {
                selectionConsumer.consume(targets);
            }

            @Override
            public void computeUsages(@NotNull List<? extends PsiElement> targets) {
                for (LoomaDirective tag : block.getTags()) addOccurrence(tag.getNameElement());
            }
        };
    }
}
