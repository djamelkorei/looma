package io.github.djamelkorei.looma.intellij.java;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import io.github.djamelkorei.looma.intellij.psi.LoomaBlock;
import io.github.djamelkorei.looma.intellij.psi.LoomaDirective;
import io.github.djamelkorei.looma.intellij.psi.LoomaFiles;
import io.github.djamelkorei.looma.intellij.psi.LoomaStringLiteral;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Form binding: {@code @bind('form')} points to where the model gets {@code form}, and the paths of
 * {@code @field('address.city')} & co. to the getters of the bound object, one reference per segment.
 */
public final class LoomaJavaFormReferenceContributor extends PsiReferenceContributor {

    /** Directives whose first argument is a field path of the bound object; {@code @error} works on them too. */
    private static final Set<String> FIELDS = Set.of("field", "name", "value", "checkbox", "radio", "option", "error");

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiElement(LoomaStringLiteral.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                    @NotNull ProcessingContext context) {
                LoomaStringLiteral literal = (LoomaStringLiteral) element;
                LoomaDirective directive = literal.getDirective();
                if (directive == null || literal.argumentIndex() != 0) return PsiReference.EMPTY_ARRAY;
                if (directive.getName().equals("bind")) return new PsiReference[] {new BoundObjectReference(literal)};
                if (!FIELDS.contains(directive.getName())) return PsiReference.EMPTY_ARRAY;
                LoomaStringLiteral bound = boundObject(literal);
                if (bound == null) return PsiReference.EMPTY_ARRAY;
                List<PsiReference> refs = new ArrayList<>();
                String value = literal.getValue();
                int offset = literal.getValueRange().getStartOffset();
                int start = 0;
                for (int i = 0; i <= value.length(); i++) {
                    if (i == value.length() || value.charAt(i) == '.' || value.charAt(i) == '[') {
                        if (i > start) refs.add(new PathReference(literal, TextRange.create(offset + start, offset + i), bound, refs.size()));
                        if (i < value.length() && value.charAt(i) == '[') {
                            while (i < value.length() && value.charAt(i) != ']') i++;
                        }
                        start = i + 1;
                    }
                }
                return refs.toArray(PsiReference.EMPTY_ARRAY);
            }
        });
    }

    /** The {@code 'form'} of the {@code @bind} around {@code literal}. */
    private static @Nullable LoomaStringLiteral boundObject(LoomaStringLiteral literal) {
        for (LoomaBlock b = PsiTreeUtil.getParentOfType(literal, LoomaBlock.class); b != null;
                b = PsiTreeUtil.getParentOfType(b, LoomaBlock.class)) {
            if (b.getName().equals("bind") && b.getOpener().getArguments() != null) {
                return b.getOpener().getArguments().literal(0);
            }
        }
        return null;
    }

    private static @Nullable PsiType boundType(LoomaStringLiteral bound) {
        PsiFile template = LoomaFiles.template(bound);
        LoomaJavaModel.Attribute attribute = template == null ? null : LoomaJavaModel.attributes(template).get(bound.getValue());
        return attribute == null ? null : attribute.type();
    }

    /** {@code 'form'} in {@code @bind('form')}: the model attribute. */
    private static final class BoundObjectReference extends PsiReferenceBase<LoomaStringLiteral> {
        BoundObjectReference(LoomaStringLiteral element) {
            super(element, element.getValueRange(), true);
        }

        @Override
        public @Nullable PsiElement resolve() {
            PsiFile template = LoomaFiles.template(getElement());
            LoomaJavaModel.Attribute a = template == null ? null : LoomaJavaModel.attributes(template).get(getElement().getValue());
            return a == null ? null : a.target();
        }

        @Override
        public Object @NotNull [] getVariants() {
            PsiFile template = LoomaFiles.template(getElement());
            if (template == null) return EMPTY_ARRAY;
            return LoomaJavaModel.attributes(template).values().stream()
                    .map(a -> LookupElementBuilder.create(a.name()).withIcon(AllIcons.Nodes.Variable)
                            .withTypeText(a.type() == null ? null : a.type().getPresentableText(), true))
                    .toArray();
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement element) {
            return !(element instanceof PsiFileSystemItem) && super.isReferenceTo(element);
        }
    }

    /** One segment of a field path: {@code city} in {@code 'address.city'}. */
    private static final class PathReference extends PsiReferenceBase<LoomaStringLiteral> {
        private final LoomaStringLiteral bound;
        private final int segment;

        PathReference(LoomaStringLiteral element, TextRange range, LoomaStringLiteral bound, int segment) {
            super(element, range, true);
            this.bound = bound;
            this.segment = segment;
        }

        /** The type the segment is read on: the bound object, then each previous property's type. */
        private @Nullable PsiType ownerType() {
            PsiType type = boundType(bound);
            PsiReference[] refs = getElement().getReferences();
            for (PsiReference ref : refs) {
                if (!(ref instanceof PathReference path) || path.segment >= segment) continue;
                if (type == null) return null;
                LoomaJavaTypes.Member m = LoomaJavaTypes.member(type, path.getValue(), false, getElement());
                type = m == null ? null : m.type();
                if (type instanceof PsiArrayType || (type != null && com.intellij.psi.util.InheritanceUtil.isInheritor(type, "java.util.List"))) {
                    type = LoomaJavaTypes.elementType(type, getElement()); // items[0].name
                }
            }
            return type;
        }

        @Override
        public @Nullable PsiElement resolve() {
            PsiType type = ownerType();
            LoomaJavaTypes.Member m = type == null ? null : LoomaJavaTypes.member(type, getValue(), false, getElement());
            return m == null ? null : m.element();
        }

        @Override
        public Object @NotNull [] getVariants() {
            PsiType type = ownerType();
            if (type == null) return EMPTY_ARRAY;
            return LoomaJavaTypes.members(type, getElement()).stream().filter(m -> !m.method())
                    .map(m -> LookupElementBuilder.create(m.element(), m.name()).withIcon(AllIcons.Nodes.Property)
                            .withTypeText(m.type() == null ? null : m.type().getPresentableText(), true))
                    .toArray();
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement element) {
            return !(element instanceof PsiFileSystemItem) && super.isReferenceTo(element);
        }
    }
}
