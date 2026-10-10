package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileType;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import org.jetbrains.annotations.NotNull;

public final class LoomaPsiFile extends PsiFileBase {

    public LoomaPsiFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, LoomaLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return LoomaFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Looma file";
    }
}
