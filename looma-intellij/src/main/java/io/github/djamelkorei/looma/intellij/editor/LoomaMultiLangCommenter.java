package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.MultipleLangCommentProvider;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileViewProvider;

/** Commenting a line of a template uses a Looma comment, also on HTML lines. */
public final class LoomaMultiLangCommenter implements MultipleLangCommentProvider {

    private static final Commenter COMMENTER = new LoomaCommenter();

    @Override
    public Commenter getLineCommenter(PsiFile file, Editor editor, Language lineStartLanguage, Language lineEndLanguage) {
        return COMMENTER;
    }

    @Override
    public boolean canProcess(PsiFile file, FileViewProvider viewProvider) {
        return viewProvider instanceof LoomaFileViewProvider;
    }
}
