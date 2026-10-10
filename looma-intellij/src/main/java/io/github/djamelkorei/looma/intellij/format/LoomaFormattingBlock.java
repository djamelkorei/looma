package io.github.djamelkorei.looma.intellij.format;

import com.intellij.formatting.*;
import com.intellij.formatting.templateLanguages.BlockWithParent;
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper;
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock;
import com.intellij.formatting.templateLanguages.TemplateLanguageBlockFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.formatter.xml.HtmlPolicy;
import com.intellij.psi.formatter.xml.SyntheticBlock;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTag;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import io.github.djamelkorei.looma.intellij.psi.LoomaElementTypes;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A Looma node in the formatting model; HTML around and inside it comes in as foreign blocks. */
final class LoomaFormattingBlock extends TemplateLanguageBlock {

    /** Kept exactly as written: what is inside a directive or echo is an expression, not layout. */
    private static final TokenSet LEAVES = TokenSet.create(LoomaElementTypes.DIRECTIVE, LoomaElementTypes.ECHO,
            LoomaElementTypes.RAW_ECHO, LoomaTokenTypes.COMMENT, LoomaTokenTypes.ESCAPE);

    private final HtmlPolicy policy;

    LoomaFormattingBlock(@NotNull ASTNode node, @Nullable Wrap wrap, @Nullable Alignment alignment,
            @NotNull TemplateLanguageBlockFactory factory, @NotNull CodeStyleSettings settings,
            @Nullable List<DataLanguageBlockWrapper> foreignChildren, HtmlPolicy policy) {
        super(node, wrap, alignment, factory, settings, foreignChildren);
        this.policy = policy;
    }

    @Override
    protected IElementType getTemplateTextElementType() {
        return LoomaTokenTypes.TEMPLATE_TEXT;
    }

    @Override
    protected List<Block> buildChildren() {
        return LEAVES.contains(myNode.getElementType()) ? AbstractBlock.EMPTY : super.buildChildren();
    }

    /** Blank HTML between Looma tags is whitespace to format, not a block to keep. */
    @Override
    protected boolean shouldBuildBlockFor(ASTNode child) {
        if (child.getElementType() == LoomaTokenTypes.TEMPLATE_TEXT && child.getText().isBlank()) return false;
        return super.shouldBuildBlockFor(child);
    }

    @Override
    public boolean isLeaf() {
        return LEAVES.contains(myNode.getElementType()) || super.isLeaf();
    }

    /**
     * Inside an HTML element, HTML in a block body is already one level in (it is a child of that element), so
     * the body adds nothing and its Looma items add the level themselves. Elsewhere the body is the level.
     */
    @Override
    public Indent getIndent() {
        if (myNode.getText().isBlank()) return Indent.getNoneIndent();
        if (myNode.getElementType() == LoomaElementTypes.BODY) {
            return insideIndentingHtml(myNode) ? Indent.getNoneIndent() : Indent.getNormalIndent();
        }
        // A Looma construct directly inside an HTML element is indented like the element's children.
        DataLanguageBlockWrapper foreignParent = foreignParent();
        if (foreignParent != null) {
            if (foreignParent.getNode() instanceof XmlTag tag && !policy.indentChildrenOf(tag)) {
                return Indent.getNoneIndent();
            }
            return Indent.getNormalIndent();
        }
        ASTNode parent = myNode.getTreeParent();
        if (parent != null && parent.getElementType() == LoomaElementTypes.BODY) {
            return insideIndentingHtml(parent) ? Indent.getNormalIndent() : Indent.getNoneIndent();
        }
        return Indent.getNoneIndent();
    }

    /** True if the block around {@code body} sits in an HTML element that indents its children. */
    private boolean insideIndentingHtml(ASTNode body) {
        ASTNode block = body.getTreeParent();
        if (block == null) return false;
        PsiFile file = block.getPsi().getContainingFile();
        PsiElement html = file.getViewProvider().findElementAt(block.getStartOffset(), HTMLLanguage.INSTANCE);
        XmlTag tag = PsiTreeUtil.getParentOfType(html, XmlTag.class);
        return tag != null && policy.indentChildrenOf(tag);
    }

    /** Enter inside a block body (or between a block's tags) indents one level. */
    @Override
    public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
        IElementType type = myNode.getElementType();
        if (type == LoomaElementTypes.BODY) return new ChildAttributes(Indent.getNoneIndent(), null);
        if (type == LoomaElementTypes.BLOCK) return new ChildAttributes(Indent.getNormalIndent(), null);
        return new ChildAttributes(Indent.getNoneIndent(), null);
    }

    /** The HTML block this one sits in directly, if any (not through another Looma block). */
    private @Nullable DataLanguageBlockWrapper foreignParent() {
        for (BlockWithParent parent = getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof DataLanguageBlockWrapper wrapper && !(wrapper.getOriginal() instanceof SyntheticBlock)) {
                return wrapper;
            }
            if (parent instanceof LoomaFormattingBlock) return null;
        }
        return null;
    }
}
