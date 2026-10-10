package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileViewProvider;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import io.github.djamelkorei.looma.intellij.psi.LoomaBlock;
import io.github.djamelkorei.looma.intellij.psi.LoomaDirective;
import org.jetbrains.annotations.NotNull;

/**
 * Enter after a line ending in a block tag ({@code @if(...)}, {@code @else}) indents the new line one level
 * from that tag; if the block's next tag was right after the caret, it moves to its own line.
 */
public final class LoomaEnterHandler extends EnterHandlerDelegateAdapter {

    @Override
    public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext context) {
        if (!(file.getViewProvider() instanceof LoomaFileViewProvider)) return Result.Continue;
        Document document = editor.getDocument();
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
        int caret = editor.getCaretModel().getOffset();
        int line = document.getLineNumber(caret);
        if (line == 0) return Result.Continue;
        int prevStart = document.getLineStartOffset(line - 1);
        int prevEnd = document.getLineEndOffset(line - 1);
        CharSequence text = document.getCharsSequence();
        int last = prevEnd - 1;
        while (last >= prevStart && Character.isWhitespace(text.charAt(last))) last--;
        if (last < prevStart) return Result.Continue;

        PsiElement token = file.getViewProvider().findElementAt(last, LoomaLanguage.INSTANCE);
        LoomaDirective tag = null;
        for (PsiElement e = token; e != null && !(e instanceof PsiFile); e = e.getParent()) {
            if (e instanceof LoomaDirective d) {
                tag = d;
                break;
            }
        }
        if (tag == null || tag.getTextRange().getEndOffset() != last + 1) return Result.Continue;
        LoomaBlock block = tag.getBlock();
        if (block == null || block.getTags().getLast() == tag) return Result.Continue; // an end tag, or inline

        String base = indentOf(text, prevStart);
        String indent = base + indentUnit(file);
        int lineStart = document.getLineStartOffset(line);
        int contentStart = lineStart;
        while (contentStart < text.length() && (text.charAt(contentStart) == ' ' || text.charAt(contentStart) == '\t')) {
            contentStart++;
        }
        String rest = text.subSequence(contentStart, document.getLineEndOffset(line)).toString();
        boolean nextTagFollows = false;
        int index = block.getTags().indexOf(tag);
        if (index + 1 < block.getTags().size()) {
            LoomaDirective next = block.getTags().get(index + 1);
            nextTagFollows = next.getTextRange().getStartOffset() == contentStart
                    || rest.startsWith(next.getNameElement().getText());
        }
        document.replaceString(lineStart, contentStart, indent + (nextTagFollows ? "\n" + base : ""));
        editor.getCaretModel().moveToOffset(lineStart + indent.length());
        return Result.Stop;
    }

    private static String indentOf(CharSequence text, int lineStart) {
        int i = lineStart;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.subSequence(lineStart, i).toString();
    }

    /** One level, as configured for HTML. */
    private static String indentUnit(PsiFile file) {
        CommonCodeStyleSettings.IndentOptions options = CodeStyle.getSettings(file).getIndentOptions(HtmlFileType.INSTANCE);
        return options.USE_TAB_CHARACTER ? "\t" : " ".repeat(options.INDENT_SIZE);
    }
}
