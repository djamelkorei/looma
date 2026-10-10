package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import org.jetbrains.annotations.NotNull;

/** {@code {{ expr }}} or {@code {!! expr !!}}. */
public final class LoomaEcho extends LoomaPsiElement {

    public LoomaEcho(@NotNull ASTNode node) {
        super(node);
    }

    public boolean isRaw() {
        return getNode().getElementType() == LoomaElementTypes.RAW_ECHO;
    }

    public boolean isClosed() {
        ASTNode last = getNode().getLastChildNode();
        return last != null && (last.getElementType() == LoomaTokenTypes.ECHO_CLOSE
                || last.getElementType() == LoomaTokenTypes.RAW_CLOSE);
    }
}
