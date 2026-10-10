package io.github.djamelkorei.looma.intellij.structure;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import io.github.djamelkorei.looma.intellij.psi.LoomaBlock;
import io.github.djamelkorei.looma.intellij.psi.LoomaDirective;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** A block or a notable directive ({@code @extends}, {@code @include}, {@code @yield}, ...). */
final class LoomaStructureElement implements StructureViewTreeElement {

    /** Inline directives worth showing. */
    private static final Set<String> SHOWN = Set.of("extends", "include", "yield", "stack", "section", "lang");

    private final PsiElement element;

    LoomaStructureElement(PsiElement element) {
        this.element = element;
    }

    @Override public Object getValue() { return element; }

    @Override
    public void navigate(boolean requestFocus) {
        if (element instanceof Navigatable n) n.navigate(requestFocus);
    }

    @Override public boolean canNavigate() { return element instanceof Navigatable n && n.canNavigate(); }
    @Override public boolean canNavigateToSource() { return element instanceof Navigatable n && n.canNavigateToSource(); }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        if (element instanceof PsiFile file) return new PresentationData(file.getName(), null, LoomaIcons.FILE, null);
        LoomaDirective directive = element instanceof LoomaBlock b ? b.getOpener() : (LoomaDirective) element;
        String text = directive.getText().replaceAll("\\s+", " ");
        if (text.length() > 60) text = text.substring(0, 57) + "...";
        return new PresentationData(text, null, LoomaIcons.FILE, null);
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
        PsiElement root = element instanceof PsiFile file ? file.getViewProvider().getPsi(LoomaLanguage.INSTANCE) : element;
        if (root == null || element instanceof LoomaDirective) return EMPTY_ARRAY;
        List<TreeElement> children = new ArrayList<>();
        collect(root, children);
        return children.toArray(EMPTY_ARRAY);
    }

    private void collect(PsiElement parent, List<TreeElement> out) {
        for (PsiElement child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof LoomaBlock) {
                out.add(new LoomaStructureElement(child));
            } else if (child instanceof LoomaDirective d) {
                boolean opener = parent instanceof LoomaBlock b && b.getOpener() == d;
                if (!opener && d.getBlock() == null && SHOWN.contains(d.getName())) out.add(new LoomaStructureElement(d));
            } else if (PsiTreeUtil.findChildOfAnyType(child, LoomaBlock.class, LoomaDirective.class) != null) {
                collect(child, out);
            }
        }
    }
}
