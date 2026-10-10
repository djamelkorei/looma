package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code (a, b)} of a directive. */
public final class LoomaArguments extends LoomaPsiElement {

    private static final TokenSet OPENING =
            TokenSet.create(LoomaTokenTypes.LPAREN, LoomaTokenTypes.LBRACKET, LoomaTokenTypes.LBRACE);
    private static final TokenSet CLOSING =
            TokenSet.create(LoomaTokenTypes.RPAREN, LoomaTokenTypes.RBRACKET, LoomaTokenTypes.RBRACE);

    public LoomaArguments(@NotNull ASTNode node) {
        super(node);
    }

    /** The top-level arguments, each as the list of its elements (whitespace excluded). */
    public @NotNull List<List<PsiElement>> split() {
        List<List<PsiElement>> args = new ArrayList<>();
        List<PsiElement> current = new ArrayList<>();
        int depth = 0;
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            ASTNode node = child.getNode();
            if (node.getElementType() == LoomaTokenTypes.WHITE_SPACE) continue;
            if (OPENING.contains(node.getElementType()) && depth++ == 0) continue;
            if (CLOSING.contains(node.getElementType()) && --depth == 0) continue;
            if (node.getElementType() == LoomaTokenTypes.COMMA && depth == 1) {
                args.add(current);
                current = new ArrayList<>();
                continue;
            }
            current.add(child);
        }
        if (!current.isEmpty() || !args.isEmpty()) args.add(current);
        return args;
    }

    /** The argument at {@code index} if it is just a string literal. */
    public @Nullable LoomaStringLiteral literal(int index) {
        List<List<PsiElement>> args = split();
        if (index >= args.size() || args.get(index).size() != 1) return null;
        return args.get(index).get(0) instanceof LoomaStringLiteral s ? s : null;
    }

    public int count() {
        return split().size();
    }
}
