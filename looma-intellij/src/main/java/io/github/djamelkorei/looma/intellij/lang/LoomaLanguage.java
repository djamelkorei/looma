package io.github.djamelkorei.looma.intellij.lang;

import com.intellij.lang.Language;
import com.intellij.psi.templateLanguages.TemplateLanguage;

public final class LoomaLanguage extends Language implements TemplateLanguage {

    public static final LoomaLanguage INSTANCE = new LoomaLanguage();

    private LoomaLanguage() {
        super("Looma");
    }

    @Override
    public String getDisplayName() {
        return "Looma";
    }
}
