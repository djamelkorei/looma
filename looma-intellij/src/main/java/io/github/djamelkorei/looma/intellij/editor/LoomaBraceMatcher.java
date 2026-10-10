package io.github.djamelkorei.looma.intellij.editor;

import static io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes.*;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LoomaBraceMatcher implements PairedBraceMatcher {

    private static final BracePair[] PAIRS = {
            new BracePair(ECHO_OPEN, ECHO_CLOSE, true),
            new BracePair(RAW_OPEN, RAW_CLOSE, true),
            new BracePair(LPAREN, RPAREN, false),
            new BracePair(LBRACKET, RBRACKET, false),
            new BracePair(LBRACE, RBRACE, false),
    };

    @Override public BracePair @NotNull [] getPairs() { return PAIRS; }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }
}
