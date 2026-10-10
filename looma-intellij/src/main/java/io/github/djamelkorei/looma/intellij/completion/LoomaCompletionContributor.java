package io.github.djamelkorei.looma.intellij.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives.Directive;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives.Syntax;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileViewProvider;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import io.github.djamelkorei.looma.intellij.lang.LoomaProjectDirectives;
import io.github.djamelkorei.looma.intellij.psi.LoomaBlock;
import io.github.djamelkorei.looma.intellij.psi.LoomaMember;
import io.github.djamelkorei.looma.intellij.psi.LoomaScopes;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Directives after '@' (anywhere in a template, also inside HTML) and the members of {@code $loop} and
 * {@code $attributes}.
 */
public final class LoomaCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        if (parameters.getCompletionType() != CompletionType.BASIC) return;
        PsiFile file = parameters.getOriginalFile();
        if (!(file.getViewProvider() instanceof LoomaFileViewProvider)) return;
        PsiElement position = parameters.getPosition();
        if (position.getParent() instanceof LoomaMember member) {
            members(member, result);
            return;
        }
        if (PlatformPatterns.psiElement().inside(LoomaVariable.class).accepts(position)) return; // references complete these
        CharSequence text = parameters.getEditor().getDocument().getCharsSequence();
        int offset = parameters.getOffset();
        int start = offset;
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) start--;
        if (start == 0 || text.charAt(start - 1) != '@' || (start > 1 && text.charAt(start - 2) == '@')) return;
        if (start > 1 && Character.isLetterOrDigit(text.charAt(start - 2))) return; // an e-mail address
        directives(file, offset, result.withPrefixMatcher(text.subSequence(start, offset).toString()));
        result.stopHere();
    }

    private static void directives(PsiFile file, int offset, CompletionResultSet result) {
        LoomaDirectives directives = LoomaProjectDirectives.get(file.getProject());
        Map<String, Directive> tags = new LinkedHashMap<>(); // secondary tags of the blocks around, by name
        PsiElement at = file.getViewProvider().findElementAt(Math.max(0, offset - 1), LoomaLanguage.INSTANCE);
        for (LoomaBlock b = PsiTreeUtil.getParentOfType(at, LoomaBlock.class); b != null;
                b = PsiTreeUtil.getParentOfType(b, LoomaBlock.class)) {
            Directive d = directives.directive(b.getName());
            if (d == null) continue;
            d.middle().keySet().forEach(t -> tags.putIfAbsent(t, d));
            d.ends().keySet().forEach(t -> tags.putIfAbsent(t, d));
        }
        int priority = 100;
        for (Map.Entry<String, Directive> tag : tags.entrySet()) {
            result.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(tag.getValue(), tag.getKey())
                    .withPresentableText("@" + tag.getKey()).withIcon(LoomaIcons.FILE).withBoldness(true)
                    .withTypeText("@" + tag.getValue().name(), true), priority--));
        }
        for (Directive d : directives.all()) {
            result.addElement(directive(d));
        }
    }

    private static LookupElement directive(Directive d) {
        return LookupElementBuilder.create(d, d.name())
                .withPresentableText("@" + d.name())
                .withTailText(d.syntax() == Syntax.NONE ? null : d.syntax() == Syntax.OPTIONAL ? "[(...)]" : "(...)", true)
                .withTypeText(d.isBlock() ? "block" : null, true)
                .withIcon(LoomaIcons.FILE)
                .withInsertHandler(LoomaCompletionContributor::insertDirective);
    }

    /** {@code @if(|)} plus {@code @endif} on its own line for blocks; {@code @once} gets an empty body line. */
    private static void insertDirective(InsertionContext context, LookupElement item) {
        if (!(item.getObject() instanceof Directive d)) return;
        Document document = context.getDocument();
        int tail = context.getTailOffset();
        CharSequence text = document.getCharsSequence();
        boolean hasParen = tail < text.length() && text.charAt(tail) == '(';
        String indent = indentOf(text, context.getStartOffset());
        String end = d.isBlock() ? "@" + d.ends().keySet().stream().sorted().findFirst().orElseThrow() : null;
        if (d.syntax() == Syntax.REQUIRED && !hasParen) {
            document.insertString(tail, "()" + (end == null ? "" : "\n" + indent + end));
            context.getEditor().getCaretModel().moveToOffset(tail + 1);
        } else if (end != null && !hasParen) {
            String body = "\n" + indent + "    ";
            document.insertString(tail, body + "\n" + indent + end);
            context.getEditor().getCaretModel().moveToOffset(tail + body.length());
        }
    }

    private static String indentOf(CharSequence text, int offset) {
        int lineStart = offset;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int i = lineStart;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.subSequence(lineStart, i).toString();
    }

    private static void members(LoomaMember member, CompletionResultSet result) {
        if (!(member.getQualifier() instanceof LoomaVariable qualifier)) return;
        Map<String, String> members = switch (qualifier.getName()) {
            case "loop" -> LoomaScopes.LOOP_PROPERTIES;
            case "attributes" -> LoomaScopes.ATTRIBUTE_METHODS;
            default -> Map.of();
        };
        boolean methods = members == LoomaScopes.ATTRIBUTE_METHODS;
        members.forEach((name, description) -> result.addElement(LookupElementBuilder.create(name)
                .withTypeText(description, true)
                .withTailText(methods ? "(...)" : null, true)));
    }
}
