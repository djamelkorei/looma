package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Facts about whole templates: their directives with literal names and their layout chain. */
public final class LoomaFiles {

    private LoomaFiles() {}

    /** The Looma tree of a template, or null if {@code file} is not one. */
    public static @Nullable LoomaPsiFile looma(@Nullable PsiFile file) {
        if (file == null) return null;
        return file.getViewProvider().getPsi(LoomaLanguage.INSTANCE) instanceof LoomaPsiFile looma ? looma : null;
    }

    public static @Nullable LoomaPsiFile looma(@NotNull PsiElement context, @NotNull VirtualFile vf) {
        return looma(PsiManager.getInstance(context.getProject()).findFile(vf));
    }

    /** Every directive of the file, in order (cached until the file changes). */
    public static @NotNull List<LoomaDirective> directives(@NotNull LoomaPsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                List.copyOf(PsiTreeUtil.findChildrenOfType(file, LoomaDirective.class)), file));
    }

    /** The first-argument literals of the directives named {@code names}, e.g. all {@code @stack('x')} names. */
    public static @NotNull List<LoomaStringLiteral> names(@NotNull LoomaPsiFile file, @NotNull Set<String> names) {
        List<LoomaStringLiteral> out = new ArrayList<>();
        for (LoomaDirective d : directives(file)) {
            if (names.contains(d.getName()) && d.getArguments() != null) {
                LoomaStringLiteral literal = d.getArguments().literal(0);
                if (literal != null) out.add(literal);
            }
        }
        return out;
    }

    /** The layout named by {@code @extends('...')}, or null. */
    public static @Nullable LoomaPsiFile layout(@NotNull LoomaPsiFile file) {
        for (LoomaStringLiteral name : names(file, Set.of("extends"))) {
            return looma(LoomaTemplates.find(file, name.getValue()));
        }
        return null;
    }

    /** The layouts above {@code file}, nearest first. */
    public static @NotNull List<LoomaPsiFile> layoutChain(@NotNull LoomaPsiFile file) {
        List<LoomaPsiFile> chain = new ArrayList<>();
        Set<LoomaPsiFile> seen = new HashSet<>(Set.of(file));
        for (LoomaPsiFile layout = layout(file); layout != null && seen.add(layout); layout = layout(layout)) {
            chain.add(layout);
        }
        return chain;
    }

    /** Templates that extend {@code layout}, directly or through other layouts. */
    public static @NotNull List<LoomaPsiFile> descendants(@NotNull LoomaPsiFile layout) {
        List<LoomaPsiFile> out = new ArrayList<>();
        for (VirtualFile vf : LoomaTemplates.all(layout).values()) {
            LoomaPsiFile file = looma(layout, vf);
            if (file != null && file != layout && layoutChain(file).contains(layout)) out.add(file);
        }
        return out;
    }

    /** Every template visible from {@code context}. */
    public static @NotNull List<LoomaPsiFile> all(@NotNull PsiElement context) {
        List<LoomaPsiFile> out = new ArrayList<>();
        for (VirtualFile vf : LoomaTemplates.all(context).values()) {
            LoomaPsiFile file = looma(context, vf);
            if (file != null) out.add(file);
        }
        return out;
    }
}
