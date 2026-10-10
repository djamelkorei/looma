package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class LoomaPsiElement extends ASTWrapperPsiElement {

    public LoomaPsiElement(@NotNull ASTNode node) {
        super(node);
    }
}
