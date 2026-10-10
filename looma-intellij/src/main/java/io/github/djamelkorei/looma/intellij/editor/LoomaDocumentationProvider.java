package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives.Directive;
import io.github.djamelkorei.looma.intellij.lang.LoomaProjectDirectives;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import io.github.djamelkorei.looma.intellij.psi.LoomaMember;
import io.github.djamelkorei.looma.intellij.psi.LoomaScopes;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariable;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariableDeclaration;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Quick documentation for directives (also in completion), {@code $loop} and the implicit variables. */
public final class LoomaDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
            @Nullable PsiElement contextElement, int targetOffset) {
        if (contextElement == null || contextElement.getNode() == null) return null;
        if (contextElement.getNode().getElementType() == LoomaTokenTypes.DIRECTIVE) return contextElement;
        PsiElement parent = contextElement.getParent();
        if (parent instanceof LoomaMember || parent instanceof LoomaVariable) return parent;
        return null;
    }

    @Override
    public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object,
            PsiElement element) {
        return object instanceof Directive d ? new DirectiveElement(psiManager, d) : null;
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element instanceof DirectiveElement d) return directiveDoc(d.directive, null);
        if (element.getNode() != null && element.getNode().getElementType() == LoomaTokenTypes.DIRECTIVE) {
            return directiveDoc(element.getText().substring(1), element);
        }
        if (element instanceof LoomaMember member && member.getQualifier() instanceof LoomaVariable q) {
            String description = switch (q.getName()) {
                case "loop" -> LoomaScopes.LOOP_PROPERTIES.get(member.getName());
                case "attributes" -> LoomaScopes.ATTRIBUTE_METHODS.get(member.getName());
                default -> LoomaScopes.HELPERS.getOrDefault(q.getName(), Map.of()).get(member.getName());
            };
            return description == null ? null : doc("$" + q.getName() + "->" + member.getName(), null, description);
        }
        String name = element instanceof LoomaVariable v ? v.getName()
                : originalElement != null && originalElement.getParent() instanceof LoomaVariable v ? v.getName() : null;
        if (name == null && element instanceof LoomaVariableDeclaration d) name = d.getName();
        if (name != null) {
            String description = LoomaScopes.visible(originalElement != null ? originalElement : element).get(name);
            if (description != null) return doc("$" + name, null, StringUtil.escapeXmlEntities(description));
        }
        return null;
    }

    private static String directiveDoc(String name, @Nullable PsiElement context) {
        LoomaDirectives directives = LoomaProjectDirectives.get(context == null ? null : context.getProject());
        Directive d = directives.directive(name);
        if (d != null) return directiveDoc(d, null);
        for (Directive owner : directives.all()) {
            if (owner.middle().containsKey(name) || owner.ends().containsKey(name)) return directiveDoc(owner, name);
        }
        return null;
    }

    private static String directiveDoc(Directive d, @Nullable String tag) {
        String title = tag == null ? "@" + d.name() : "@" + tag + " (part of @" + d.name() + ")";
        return doc(title, d.usage(), d.doc());
    }

    private static String doc(String title, @Nullable String usage, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START).append(StringUtil.escapeXmlEntities(title))
                .append(DocumentationMarkup.DEFINITION_END);
        sb.append(DocumentationMarkup.CONTENT_START).append(body);
        if (usage != null) sb.append("<p><code>").append(StringUtil.escapeXmlEntities(usage)).append("</code>");
        sb.append(DocumentationMarkup.CONTENT_END);
        return sb.toString();
    }

    /** Stands for a directive in the completion list. */
    private static final class DirectiveElement extends FakePsiElement {
        private final PsiManager manager;
        private final Directive directive;

        DirectiveElement(PsiManager manager, Directive directive) {
            this.manager = manager;
            this.directive = directive;
        }

        @Override public PsiElement getParent() { return null; }
        @Override public PsiManager getManager() { return manager; }
        @Override public String getName() { return "@" + directive.name(); }
    }
}
