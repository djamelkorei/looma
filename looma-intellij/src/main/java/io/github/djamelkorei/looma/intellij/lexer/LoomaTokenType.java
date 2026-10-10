package io.github.djamelkorei.looma.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class LoomaTokenType extends IElementType {

    public LoomaTokenType(@NotNull @NonNls String debugName) {
        super(debugName, LoomaLanguage.INSTANCE);
    }
}
