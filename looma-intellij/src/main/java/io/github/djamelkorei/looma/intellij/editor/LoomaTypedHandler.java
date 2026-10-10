package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Closes echoes as they are typed: {@code {{} gives {@code {{ | }}}, {@code {!!} gives {@code {!! | !!}},
 * and a '-' in an empty auto-closed echo turns it into a comment {@code {{-- | --}}}.
 */
public final class LoomaTypedHandler extends TypedHandlerDelegate {

    @Override
    public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor,
            @NotNull PsiFile file, @NotNull FileType fileType) {
        if (!(file.getViewProvider() instanceof LoomaFileViewProvider) || editor.getSelectionModel().hasSelection()) {
            return Result.CONTINUE;
        }
        Document document = editor.getDocument();
        int caret = editor.getCaretModel().getOffset();
        CharSequence text = document.getCharsSequence();
        if (c == '{' && endsWith(text, caret, "{") && !endsWith(text, caret, "@{") && !endsWith(text, caret, "{{")
                && !startsWith(text, caret, "}") && !startsWith(text, caret, " }}")) {
            replace(editor, caret, caret, "{  }}", 2);
        } else if (c == '!' && endsWith(text, caret, "{!") && !endsWith(text, caret, "{!!")) {
            replace(editor, caret, caret, "!  !!}", 2);
        } else if (c == '-' && endsWith(text, caret, "{{ ") && startsWith(text, caret, " }}")) {
            replace(editor, caret - 3, caret + 3, "{{--  --}}", 5);
        } else {
            return Result.CONTINUE;
        }
        return Result.STOP;
    }

    private static void replace(Editor editor, int start, int end, String text, int caretInText) {
        editor.getDocument().replaceString(start, end, text);
        editor.getCaretModel().moveToOffset(start + caretInText);
    }

    private static boolean endsWith(CharSequence text, int end, String s) {
        return end >= s.length() && text.subSequence(end - s.length(), end).toString().equals(s);
    }

    private static boolean startsWith(CharSequence text, int start, String s) {
        return start + s.length() <= text.length() && text.subSequence(start, start + s.length()).toString().equals(s);
    }
}
