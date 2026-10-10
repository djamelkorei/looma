package io.github.djamelkorei.looma.intellij.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileType;
import io.github.djamelkorei.looma.intellij.psi.LoomaTemplates;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A view name ({@code 'layouts.app'}) that points to a template file. Used in templates and, through
 * the Java integration, in controllers. Renaming or moving the file updates the name.
 */
public final class LoomaTemplateReference extends PsiReferenceBase<PsiElement> {

    private final String name;
    private final String prefix;

    /**
     * @param range  the view name inside {@code element}
     * @param prefix only templates under this name are offered in completion (e.g. {@code components.})
     */
    public LoomaTemplateReference(@NotNull PsiElement element, @NotNull TextRange range, boolean soft,
            @NotNull String prefix) {
        super(element, range, soft);
        this.name = range.substring(element.getText());
        this.prefix = prefix;
    }

    public @NotNull String getName() {
        return name;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return LoomaTemplates.find(getElement(), prefix + name);
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<Object> variants = new ArrayList<>();
        LoomaTemplates.all(getElement()).keySet().forEach(view -> {
            if (view.startsWith(prefix)) {
                variants.add(LookupElementBuilder.create(view.substring(prefix.length())).withIcon(LoomaIcons.FILE));
            }
        });
        return variants.toArray();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newFileName) throws IncorrectOperationException {
        if (!newFileName.endsWith(LoomaFileType.SUFFIX)) return getElement();
        String base = newFileName.substring(0, newFileName.length() - LoomaFileType.SUFFIX.length());
        int dot = name.lastIndexOf('.');
        int slash = name.lastIndexOf('/');
        int cut = Math.max(dot, slash);
        return rename(cut < 0 ? base : name.substring(0, cut + 1) + base);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        if (!(element instanceof PsiFile file)) return getElement();
        String view = LoomaTemplates.nameOf(file);
        if (view == null || !view.startsWith(prefix)) return getElement();
        return rename(view.substring(prefix.length()));
    }

    private PsiElement rename(String newName) {
        return ElementManipulators.handleContentChange(getElement(), getRangeInElement(), newName);
    }
}
