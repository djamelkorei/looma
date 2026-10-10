package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds templates by view name, the way the engine does: {@code layouts.app} is
 * {@code <templates>/layouts/app.looma.html}, components live in {@code <templates>/components/}.
 * The templates folder is the nearest ancestor named {@code templates} (the starter's default prefix),
 * else the source root.
 */
public final class LoomaTemplates {

    public static final String FOLDER = "templates";
    public static final String COMPONENTS = "components";

    /** Same rule as the engine's {@code TemplateNames}. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_\\-./]+");

    private LoomaTemplates() {}

    /** {@code layouts.app} or {@code layouts/app} to {@code layouts/app}; null if the engine would reject it. */
    public static @Nullable String toPath(@NotNull String name) {
        if (!VALID.matcher(name).matches() || name.startsWith("/") || name.contains("..")) return null;
        return name.replace('.', '/');
    }

    /** The templates folder of a template file. */
    public static @Nullable VirtualFile root(@NotNull PsiFile file) {
        VirtualFile vf = file.getOriginalFile().getVirtualFile();
        if (vf == null) return null;
        for (VirtualFile dir = vf.getParent(); dir != null; dir = dir.getParent()) {
            if (dir.getName().equals(FOLDER)) return dir;
        }
        VirtualFile sourceRoot = ProjectFileIndex.getInstance(file.getProject()).getSourceRootForFile(vf);
        return sourceRoot != null ? sourceRoot : vf.getParent();
    }

    /** Template folders to search from {@code context}: its own folder for a template, else the module's. */
    public static @NotNull List<VirtualFile> roots(@NotNull PsiElement context) {
        PsiFile file = context.getContainingFile();
        Set<VirtualFile> roots = new LinkedHashSet<>();
        if (file != null && file.getFileType() == LoomaFileType.INSTANCE) {
            VirtualFile root = root(file);
            if (root != null) roots.add(root);
            return new ArrayList<>(roots);
        }
        Module module = ModuleUtilCore.findModuleForPsiElement(context);
        if (module != null) addTemplateFolders(ModuleRootManager.getInstance(module).getSourceRoots(), roots);
        addTemplateFolders(ProjectRootManager.getInstance(context.getProject()).getContentSourceRoots(), roots);
        return new ArrayList<>(roots);
    }

    private static void addTemplateFolders(VirtualFile[] sourceRoots, Set<VirtualFile> out) {
        for (VirtualFile sourceRoot : sourceRoots) {
            VirtualFile folder = sourceRoot.findChild(FOLDER);
            if (folder != null && folder.isDirectory()) out.add(folder);
        }
    }

    /** The template called {@code name} as seen from {@code context}, or null. */
    public static @Nullable PsiFile find(@NotNull PsiElement context, @NotNull String name) {
        String path = toPath(name);
        if (path == null) return null;
        for (VirtualFile root : roots(context)) {
            VirtualFile vf = root.findFileByRelativePath(path + LoomaFileType.SUFFIX);
            if (vf != null && !vf.isDirectory()) return PsiManager.getInstance(context.getProject()).findFile(vf);
        }
        return null;
    }

    /** The template for {@code <x-name>}. */
    public static @Nullable PsiFile findComponent(@NotNull PsiElement context, @NotNull String name) {
        return find(context, COMPONENTS + "." + name);
    }

    /** Every template visible from {@code context}, by view name (dotted). */
    public static @NotNull Map<String, VirtualFile> all(@NotNull PsiElement context) {
        Map<String, VirtualFile> all = new LinkedHashMap<>();
        for (VirtualFile root : roots(context)) {
            VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
                @Override
                public boolean visitFile(@NotNull VirtualFile file) {
                    String name = nameOf(root, file);
                    if (name != null) all.putIfAbsent(name, file);
                    return true;
                }
            });
        }
        return all;
    }

    /** The view name of {@code file} below {@code root}, or null if it is not a template there. */
    public static @Nullable String nameOf(@NotNull VirtualFile root, @NotNull VirtualFile file) {
        if (file.isDirectory() || !file.getName().endsWith(LoomaFileType.SUFFIX)) return null;
        String relative = VfsUtilCore.getRelativePath(file, root, '.');
        if (relative == null) return null;
        return relative.substring(0, relative.length() - LoomaFileType.SUFFIX.length());
    }

    /** The view name of a template file, e.g. {@code layouts.app}. */
    public static @Nullable String nameOf(@NotNull PsiFile file) {
        VirtualFile root = root(file);
        VirtualFile vf = file.getOriginalFile().getVirtualFile();
        return root == null || vf == null ? null : nameOf(root, vf);
    }

    /** True for templates in the components folder, which run with their own scope. */
    public static boolean isComponent(@NotNull PsiFile file) {
        String name = nameOf(file);
        return name != null && name.startsWith(COMPONENTS + ".");
    }
}
