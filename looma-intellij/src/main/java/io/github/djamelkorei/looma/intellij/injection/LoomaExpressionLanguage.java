package io.github.djamelkorei.looma.intellij.injection;

import com.intellij.lang.Language;

/**
 * A Looma expression on its own, as injected into component attributes ({@code :count="items.size()"}).
 * Its tokens and elements are Looma's, so Looma's references and completion apply. Not a dialect of Looma:
 * that would make it a template language with an HTML tree of its own.
 */
public final class LoomaExpressionLanguage extends Language {

    public static final LoomaExpressionLanguage INSTANCE = new LoomaExpressionLanguage();

    private LoomaExpressionLanguage() {
        super("LoomaExpression");
    }

    @Override
    public String getDisplayName() {
        return "Looma expression";
    }
}
