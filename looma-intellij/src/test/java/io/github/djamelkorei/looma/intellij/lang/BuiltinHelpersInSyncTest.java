package io.github.djamelkorei.looma.intellij.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import io.github.djamelkorei.looma.intellij.java.LoomaJavaHelpers;
import io.github.djamelkorei.looma.intellij.psi.LoomaScopes;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/**
 * The built-in helpers the plugin knows ({@code helpers.txt}) match the engine's helper classes. Needs looma-core
 * compiled; regenerate the list with {@code scripts/update-helpers.sh}.
 */
public class BuiltinHelpersInSyncTest {

    @Test
    public void matchesTheEngine() throws Exception {
        for (Map.Entry<String, String> helper : LoomaJavaHelpers.BUILTIN.entrySet()) {
            Class<?> cls;
            try {
                cls = Class.forName(helper.getKey());
            } catch (ClassNotFoundException e) {
                assumeTrue("looma-core is not compiled", false);
                return;
            }
            Object instance = cls.getConstructor().newInstance();
            assertEquals(helper.getValue(), cls.getMethod("name").invoke(instance));
            Set<String> methods = new TreeSet<>();
            for (Method m : cls.getMethods()) {
                if (m.getDeclaringClass() == cls && !Modifier.isStatic(m.getModifiers()) && !m.getName().equals("name")) {
                    methods.add(m.getName());
                }
            }
            assertEquals(helper.getValue() + ": run looma-intellij/scripts/update-helpers.sh", methods, new TreeSet<>(LoomaScopes.HELPERS.get(helper.getValue()).keySet()));
        }
    }
}
