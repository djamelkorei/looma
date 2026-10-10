package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import org.jetbrains.annotations.NotNull;

/** File | New | Looma Template: a page, a layout or a component. */
public final class LoomaCreateTemplateAction extends CreateFileFromTemplateAction implements DumbAware {

    public LoomaCreateTemplateAction() {
        super("Looma Template", "Creates a Looma template", LoomaIcons.FILE);
    }

    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory,
            CreateFileFromTemplateDialog.@NotNull Builder builder) {
        builder.setTitle("New Looma Template")
                .addKind("Page", LoomaIcons.FILE, "Looma Page")
                .addKind("Layout", LoomaIcons.FILE, "Looma Layout")
                .addKind("Component", LoomaIcons.FILE, "Looma Component");
    }

    @Override
    protected @NotNull String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return "Create Looma template " + newName;
    }
}
