package io.github.djamelkorei.looma.intellij.highlight;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.djamelkorei.looma.intellij.lang.LoomaProjectDirectives;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import org.jetbrains.annotations.NotNull;

/** Highlights Looma tokens, and the HTML between them with the HTML highlighter. */
public final class LoomaEditorHighlighterProvider implements EditorHighlighterProvider {

    @Override
    public EditorHighlighter getEditorHighlighter(Project project, @NotNull FileType fileType, VirtualFile file,
            @NotNull EditorColorsScheme colors) {
        LayeredLexerEditorHighlighter highlighter = new LayeredLexerEditorHighlighter(
                new LoomaSyntaxHighlighter(LoomaProjectDirectives.get(project)), colors);
        SyntaxHighlighter html = SyntaxHighlighterFactory.getSyntaxHighlighter(HtmlFileType.INSTANCE, project, file);
        if (html != null) highlighter.registerLayer(LoomaTokenTypes.TEMPLATE_TEXT, new LayerDescriptor(html, ""));
        return highlighter;
    }
}
