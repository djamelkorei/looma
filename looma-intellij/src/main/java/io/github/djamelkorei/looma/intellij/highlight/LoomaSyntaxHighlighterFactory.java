package io.github.djamelkorei.looma.intellij.highlight;

import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.github.djamelkorei.looma.intellij.lang.LoomaProjectDirectives;
import org.jetbrains.annotations.NotNull;

public final class LoomaSyntaxHighlighterFactory extends SyntaxHighlighterFactory {

    @Override
    public @NotNull SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
        return new LoomaSyntaxHighlighter(LoomaProjectDirectives.get(project));
    }
}
