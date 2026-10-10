package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.djamelkorei.looma.intellij.psi.LoomaBlock;
import io.github.djamelkorei.looma.intellij.psi.LoomaDirective;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Folds block bodies ({@code @if(...) ... @endif}, one region per branch) and multi-line comments. */
public final class LoomaFoldingBuilder extends FoldingBuilderEx implements DumbAware {

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> out = new ArrayList<>();
        for (LoomaBlock block : PsiTreeUtil.findChildrenOfType(root, LoomaBlock.class)) {
            List<LoomaDirective> tags = block.getTags();
            for (int i = 0; i + 1 < tags.size(); i++) {
                fold(out, document, tags.get(i).getTextRange().getEndOffset(), tags.get(i + 1).getTextRange().getStartOffset(),
                        block.getNode());
            }
        }
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(root, PsiComment.class)) {
            TextRange r = comment.getTextRange();
            fold(out, document, r.getStartOffset(), r.getEndOffset(), comment.getNode());
        }
        return out.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static void fold(List<FoldingDescriptor> out, Document document, int start, int end, ASTNode node) {
        if (end - start < 2 || document.getLineNumber(start) == document.getLineNumber(end)) return;
        out.add(new FoldingDescriptor(node, TextRange.create(start, end)));
    }

    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return node.getPsi() instanceof PsiComment ? "{{-- ... --}}" : "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
