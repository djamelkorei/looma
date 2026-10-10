package io.github.djamelkorei.looma.intellij.annotator;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.djamelkorei.looma.intellij.highlight.LoomaColors;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lang.LoomaProjectDirectives;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import io.github.djamelkorei.looma.intellij.psi.*;
import io.github.djamelkorei.looma.intellij.reference.LoomaTemplateReference;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;

/**
 * Reports what the engine would reject when parsing a template (unclosed or stray tags, {@code @break}
 * outside a loop, missing templates), and colors members by kind.
 */
public final class LoomaAnnotator implements Annotator {

    /** Directives whose first argument must be a quoted name. */
    private static final Set<String> NAMED = Set.of("section", "yield", "push", "prepend", "stack");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof LoomaMember member) {
            color(holder, member, member.isCall() ? LoomaColors.METHOD : LoomaColors.PROPERTY);
        } else if (element instanceof LoomaVariable || element instanceof LoomaVariableDeclaration) {
            color(holder, element, LoomaColors.VARIABLE);
        } else if (element instanceof LoomaBlock block) {
            checkClosed(block, holder);
        } else if (element instanceof LoomaDirective directive) {
            checkDirective(directive, holder);
        } else if (element instanceof LoomaEcho echo && !echo.isClosed()) {
            error(holder, echo.getFirstChild(), "Unclosed tag, expected '" + (echo.isRaw() ? "!!}" : "}}") + "'");
        } else if (element instanceof LoomaStringLiteral literal) {
            checkTemplateReferences(literal, holder);
        }
    }

    private static void checkClosed(LoomaBlock block, AnnotationHolder holder) {
        LoomaDirectives.Directive d = directives(block).directive(block.getName());
        if (d == null) return;
        String last = block.getTags().getLast().getName();
        if (block.getTags().size() > 1 && d.ends().containsKey(last)) return;
        Set<String> ends = new TreeSet<>(d.ends().keySet());
        error(holder, block.getOpener().getNameElement(), "Unclosed block: @" + d.name() + " is never closed, expected "
                + String.join(" or ", ends.stream().map(e -> "@" + e).toList()));
    }

    private static void checkDirective(LoomaDirective directive, AnnotationHolder holder) {
        LoomaDirectives directives = directives(directive);
        String name = directive.getName();
        LoomaBlock block = directive.getBlock();
        boolean opener = block != null && block.getOpener() == directive;
        if (directives.directive(name) == null && !opener) {
            LoomaDirectives.Directive owner = block == null ? null : directives.directive(block.getName());
            boolean owned = owner != null && (owner.middle().containsKey(name) || owner.ends().containsKey(name));
            if (!owned) error(holder, directive.getNameElement(), "Unexpected @" + name);
        }
        if (name.equals("break") || name.equals("continue")) checkInLoop(directive, holder);
        LoomaArguments args = directive.getArguments();
        if (args != null) {
            ASTNode last = args.getNode().getLastChildNode();
            if (last == null || last.getElementType() != LoomaTokenTypes.RPAREN || args.getTextLength() < 2) {
                error(holder, directive.getNameElement(), "Unbalanced parentheses in directive");
            } else if (NAMED.contains(name) && args.literal(0) == null) {
                error(holder, args, "@" + name + " needs a quoted name, e.g. @" + name + "('content')");
            }
        }
    }

    private static void checkInLoop(LoomaDirective directive, AnnotationHolder holder) {
        boolean isBreak = directive.getName().equals("break");
        for (LoomaBlock b = PsiTreeUtil.getParentOfType(directive, LoomaBlock.class); b != null;
                b = PsiTreeUtil.getParentOfType(b, LoomaBlock.class)) {
            if (LoomaScopes.LOOPS.contains(b.getName()) || (isBreak && b.getName().equals("switch"))) return;
        }
        error(holder, directive.getNameElement(),
                "@" + directive.getName() + " can only be used inside a loop" + (isBreak ? " or @switch" : ""));
    }

    private static void checkTemplateReferences(LoomaStringLiteral literal, AnnotationHolder holder) {
        for (PsiReference ref : literal.getReferences()) {
            if (ref instanceof LoomaTemplateReference t && !t.isSoft() && t.resolve() == null) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Template '" + t.getName() + "' not found")
                        .range(ref.getAbsoluteRange()).create();
            }
        }
    }

    private static LoomaDirectives directives(PsiElement element) {
        return LoomaProjectDirectives.get(element.getProject());
    }

    private static void color(AnnotationHolder holder, PsiElement element, TextAttributesKey key) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(key).create();
    }

    private static void error(AnnotationHolder holder, PsiElement element, String message) {
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(element).create();
    }
}
