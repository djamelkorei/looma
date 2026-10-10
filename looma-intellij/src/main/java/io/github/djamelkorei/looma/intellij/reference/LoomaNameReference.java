package io.github.djamelkorei.looma.intellij.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import io.github.djamelkorei.looma.intellij.psi.LoomaFiles;
import io.github.djamelkorei.looma.intellij.psi.LoomaPsiFile;
import io.github.djamelkorei.looma.intellij.psi.LoomaStringLiteral;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * A section or stack name, linking the two sides: {@code @section('x')} and {@code @yield('x')} along the
 * layout chain, {@code @push('x')} / {@code @prepend('x')} and {@code @stack('x')} anywhere.
 */
public final class LoomaNameReference extends PsiPolyVariantReferenceBase<LoomaStringLiteral> {

    /** What the name is used for, deciding where the other side is. */
    public enum Kind {
        /** {@code @section} in a child: the {@code @yield}s and {@code @section...@show}s of its layouts. */
        SECTION(Set.of("yield", "section")),
        /** {@code @yield} in a layout: the {@code @section}s of the templates that extend it. */
        YIELD(Set.of("section")),
        /** {@code @push} or {@code @prepend}: the {@code @stack}s of all templates. */
        PUSH(Set.of("stack")),
        /** {@code @stack}: the {@code @push}es and {@code @prepend}s of all templates. */
        STACK(Set.of("push", "prepend"));

        final Set<String> targets;

        Kind(Set<String> targets) {
            this.targets = targets;
        }
    }

    private final Kind kind;

    public LoomaNameReference(@NotNull LoomaStringLiteral element, @NotNull Kind kind) {
        super(element, element.getValueRange(), true);
        this.kind = kind;
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        String name = getElement().getValue();
        List<ResolveResult> results = new ArrayList<>();
        for (LoomaStringLiteral candidate : candidates()) {
            if (candidate != getElement() && candidate.getValue().equals(name)) {
                results.add(new PsiElementResolveResult(candidate));
            }
        }
        return results.toArray(ResolveResult.EMPTY_ARRAY);
    }

    @Override
    public Object @NotNull [] getVariants() {
        Set<String> names = new LinkedHashSet<>();
        for (LoomaStringLiteral candidate : candidates()) {
            if (candidate != getElement()) names.add(candidate.getValue());
        }
        return names.stream().map(n -> LookupElementBuilder.create(n).withIcon(LoomaIcons.FILE)).toArray();
    }

    private List<LoomaStringLiteral> candidates() {
        LoomaPsiFile file = LoomaFiles.looma(getElement().getContainingFile());
        if (file == null) return List.of();
        List<LoomaPsiFile> files = switch (kind) {
            case SECTION -> LoomaFiles.layoutChain(file);
            case YIELD -> LoomaFiles.descendants(file);
            case PUSH, STACK -> LoomaFiles.all(file);
        };
        List<LoomaStringLiteral> out = new ArrayList<>();
        for (LoomaPsiFile f : files) {
            for (LoomaStringLiteral literal : LoomaFiles.names(f, kind.targets)) {
                if (kind != Kind.SECTION || !literal.getDirective().getName().equals("section") || isShown(literal)) {
                    out.add(literal);
                }
            }
        }
        return out;
    }

    /** A {@code @section('x') ... @show} in a layout, which acts like a yield. */
    private static boolean isShown(LoomaStringLiteral literal) {
        var block = literal.getDirective().getBlock();
        return block != null && block.getTags().getLast().getName().equals("show");
    }
}
