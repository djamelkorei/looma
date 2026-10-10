package io.github.djamelkorei.looma.intellij.component;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import io.github.djamelkorei.looma.intellij.psi.LoomaFiles;
import io.github.djamelkorei.looma.intellij.psi.LoomaPsiFile;
import io.github.djamelkorei.looma.intellij.psi.LoomaScopes;
import io.github.djamelkorei.looma.intellij.psi.LoomaTemplates;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Components: {@code <x-alert>} renders {@code components/alert}, its variables are the attributes. */
public final class LoomaComponents {

    public static final String TAG_PREFIX = "x-";
    public static final String SLOT = "slot";

    private LoomaComponents() {}

    /** {@code alert} for {@code <x-alert>}, {@code slot:title} for {@code <x-slot:title>}. */
    public static @NotNull String componentName(@NotNull XmlTag tag) {
        return tag.getName().substring(TAG_PREFIX.length());
    }

    public static boolean isSlot(@NotNull String componentName) {
        return componentName.equals(SLOT) || componentName.startsWith(SLOT + ":");
    }

    /** The template of a component tag, or null for slots and unknown components. */
    public static @Nullable LoomaPsiFile template(@NotNull XmlTag tag) {
        String name = componentName(tag);
        if (isSlot(name)) return null;
        return LoomaFiles.looma(LoomaTemplates.findComponent(tag, name));
    }

    /**
     * The variables a component template reads that are not its own (loop variables, {@code $slot}, ...):
     * these are the attributes and named slots it expects, each with its first use.
     */
    public static @NotNull Map<String, LoomaVariable> parameters(@NotNull LoomaPsiFile component) {
        return CachedValuesManager.getCachedValue(component, () -> {
            Map<String, LoomaVariable> params = new LinkedHashMap<>();
            for (LoomaVariable v : PsiTreeUtil.findChildrenOfType(component, LoomaVariable.class)) {
                String name = v.getName();
                if (LoomaScopes.COMPONENT.containsKey(name) || LoomaScopes.GLOBALS.containsKey(name)) continue;
                if (LoomaScopes.resolve(v, name) != null) continue;
                params.putIfAbsent(name, v);
            }
            return CachedValueProvider.Result.create(params, component);
        });
    }

    /** For {@code <x-slot:title>}: the {@code $title} use in the enclosing component's template. */
    public static @Nullable PsiElement slotTarget(@NotNull XmlTag slot) {
        String name = componentName(slot);
        if (!name.startsWith(SLOT + ":")) return null;
        XmlTag parent = slot.getParentTag();
        while (parent != null && (!parent.getName().startsWith(TAG_PREFIX) || isSlot(componentName(parent)))) {
            parent = parent.getParentTag();
        }
        LoomaPsiFile template = parent == null ? null : template(parent);
        return template == null ? null : parameters(template).get(name.substring(SLOT.length() + 1));
    }

    /** True if {@code file} is a template, so component tags get Looma's meaning. */
    public static boolean isTemplate(@Nullable PsiFile file) {
        return LoomaFiles.looma(file) != null;
    }
}
