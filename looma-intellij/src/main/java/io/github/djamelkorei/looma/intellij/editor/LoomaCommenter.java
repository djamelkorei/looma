package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/** {@code {{-- ... --}}}, never sent to the browser. Comment with Line Comment wraps the line in one. */
public final class LoomaCommenter implements Commenter {

    @Override public @Nullable String getLineCommentPrefix() { return null; }
    @Override public @Nullable String getBlockCommentPrefix() { return "{{--"; }
    @Override public @Nullable String getBlockCommentSuffix() { return "--}}"; }
    @Override public @Nullable String getCommentedBlockCommentPrefix() { return null; }
    @Override public @Nullable String getCommentedBlockCommentSuffix() { return null; }
}
