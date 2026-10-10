package io.github.djamelkorei.looma.intellij.component;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.xml.HtmlXmlExtension;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileViewProvider;
import org.jetbrains.annotations.NotNull;

/** HTML behavior for templates, with component-aware tag name references. */
public final class LoomaXmlExtension extends HtmlXmlExtension {

    @Override
    public boolean isAvailable(PsiFile file) {
        return file.getViewProvider() instanceof LoomaFileViewProvider;
    }

    @Override
    public TagNameReference createTagNameReference(@NotNull ASTNode nameElement, boolean startTagFlag) {
        return new LoomaTagNameReference(nameElement, startTagFlag);
    }
}
