package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.psi.tree.IElementType;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class LoomaElementType extends IElementType {

    public LoomaElementType(@NotNull @NonNls String debugName) {
        super(debugName, LoomaLanguage.INSTANCE);
    }
}
