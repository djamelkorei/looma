package io.github.djamelkorei.looma.intellij.injection;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class LoomaExpressionFile extends PsiFileBase {

    public LoomaExpressionFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, LoomaExpressionLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return PlainTextFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Looma expression";
    }
}
