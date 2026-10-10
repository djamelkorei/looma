package io.github.djamelkorei.looma.intellij.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.TemplateLanguageFileType;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public final class LoomaFileType extends LanguageFileType implements TemplateLanguageFileType {

    public static final LoomaFileType INSTANCE = new LoomaFileType();

    /** Appended to view names by the Spring starter ({@code looma.suffix}). */
    public static final String SUFFIX = ".looma.html";

    private LoomaFileType() {
        super(LoomaLanguage.INSTANCE);
    }

    @Override public @NotNull String getName() { return "Looma"; }
    @Override public @NotNull String getDescription() { return "Looma template"; }
    @Override public @NotNull String getDefaultExtension() { return "looma.html"; }
    @Override public Icon getIcon() { return LoomaIcons.FILE; }
}
