package io.github.djamelkorei.looma.intellij.java;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import io.github.djamelkorei.looma.intellij.psi.LoomaMember;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code title} in {@code $post->title}: the getter, accessor or field of the qualifier's Java type. */
final class LoomaJavaMemberReference extends PsiReferenceBase<LoomaMember> {

    private final PsiType qualifierType;

    LoomaJavaMemberReference(@NotNull LoomaMember element, @NotNull PsiType qualifierType) {
        super(element, true);
        this.qualifierType = qualifierType;
    }

    PsiType getQualifierType() {
        return qualifierType;
    }

    @Override
    public @Nullable PsiElement resolve() {
        LoomaMember member = getElement();
        LoomaJavaTypes.Member m = LoomaJavaTypes.member(qualifierType, member.getName(), member.isCall(), member);
        return m == null ? null : m.element();
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<Object> out = new ArrayList<>();
        for (LoomaJavaTypes.Member m : LoomaJavaTypes.members(qualifierType, getElement())) {
            LookupElementBuilder item = LookupElementBuilder.create(m.element(), m.name())
                    .withIcon(m.method() ? AllIcons.Nodes.Method : AllIcons.Nodes.Property)
                    .withTypeText(m.type() == null ? null : m.type().getPresentableText(), true);
            if (m.method() && m.element() instanceof PsiMethod method) {
                item = item.withTailText("(" + String.join(", ", java.util.Arrays.stream(method.getParameterList()
                        .getParameters()).map(p -> p.getType().getPresentableText()).toList()) + ")", true)
                        .withInsertHandler((ctx, i) -> {
                            if (ctx.getCompletionChar() == '(') return;
                            ctx.getDocument().insertString(ctx.getTailOffset(), "()");
                            ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset()
                                    - (method.getParameterList().isEmpty() ? 0 : 1));
                        });
            }
            out.add(item);
        }
        return out.toArray();
    }

    /** Renaming a getter renames the property: {@code getTitle} to {@code getName} gives {@code name}. */
    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        PsiElement target = resolve();
        String name = newElementName;
        if (!getElement().isCall() && target instanceof PsiMethod) {
            if (name.startsWith("get") && name.length() > 3) name = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            else if (name.startsWith("is") && name.length() > 2) name = Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return super.handleElementRename(name);
    }
}
