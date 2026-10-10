package io.github.djamelkorei.looma.intellij.java;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import io.github.djamelkorei.looma.intellij.psi.LoomaBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code @priceService} points to the {@code @TemplateAccessible} class. */
public final class LoomaJavaBeanReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiElement(LoomaBean.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                    @NotNull ProcessingContext context) {
                return new PsiReference[] {new BeanReference((LoomaBean) element)};
            }
        });
    }

    private static final class BeanReference extends PsiReferenceBase<LoomaBean> {

        BeanReference(LoomaBean element) {
            super(element, element.nameRange(), true);
        }

        @Override
        public @Nullable PsiElement resolve() {
            return LoomaJavaBeans.beans(getElement().getProject()).get(getElement().getName());
        }

        @Override
        public Object @NotNull [] getVariants() {
            return LoomaJavaBeans.beans(getElement().getProject()).entrySet().stream()
                    .map(e -> LookupElementBuilder.create(e.getValue(), e.getKey()).withIcon(AllIcons.Nodes.Class)
                            .withTypeText(e.getValue().getName(), true))
                    .toArray();
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement element) {
            return element instanceof PsiClass && super.isReferenceTo(element);
        }
    }
}
