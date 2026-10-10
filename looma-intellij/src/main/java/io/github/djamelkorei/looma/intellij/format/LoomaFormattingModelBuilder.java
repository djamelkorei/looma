package io.github.djamelkorei.looma.intellij.format;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper;
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock;
import com.intellij.formatting.templateLanguages.TemplateLanguageFormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.templateLanguages.SimpleTemplateLanguageFormattingModelBuilder;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.xml.HtmlPolicy;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reformat Code for templates: the HTML is formatted by the HTML formatter (with your HTML code style),
 * block bodies are indented one level, and directives and echoes are left as written.
 */
public final class LoomaFormattingModelBuilder extends TemplateLanguageFormattingModelBuilder {

    @Override
    public TemplateLanguageBlock createTemplateLanguageBlock(@NotNull ASTNode node, @Nullable Wrap wrap,
            @Nullable Alignment alignment, @Nullable List<DataLanguageBlockWrapper> foreignChildren,
            @NotNull CodeStyleSettings settings) {
        HtmlPolicy policy = new HtmlPolicy(settings, FormattingDocumentModelImpl.createOn(node.getPsi().getContainingFile()));
        return new LoomaFormattingBlock(node, wrap, alignment, this, settings, foreignChildren, policy);
    }

    /**
     * The HTML formatter asks for a model of each Looma fragment it meets (as for an injection); give it a
     * plain block so it can go on. The whole file is formatted from the Looma root.
     */
    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext context) {
        if (context.getNode().getElementType() == LoomaTokenTypes.OUTER_ELEMENT) {
            return new SimpleTemplateLanguageFormattingModelBuilder().createModel(context);
        }
        PsiFile file = context.getContainingFile();
        Block root = getRootBlock(file, file.getViewProvider(), context.getCodeStyleSettings());
        return new DocumentBasedFormattingModel(root, context.getProject(), context.getCodeStyleSettings(),
                file.getFileType(), file);
    }

    @Override
    public boolean dontFormatMyModel() {
        return false;
    }
}
