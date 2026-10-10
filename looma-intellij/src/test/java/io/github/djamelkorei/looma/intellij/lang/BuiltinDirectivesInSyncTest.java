package io.github.djamelkorei.looma.intellij.lang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

/**
 * The plugin's directive table must match the engine's {@code BuiltinDirectives}: same names, syntax and
 * tags. Needs looma-core compiled ({@code mvn -pl looma-core compile}); skipped otherwise.
 */
public class BuiltinDirectivesInSyncTest {

    @Test
    public void matchesTheEngine() throws Exception {
        Class<?> builtins;
        try {
            builtins = Class.forName("io.github.djamelkorei.looma.directive.BuiltinDirectives");
        } catch (ClassNotFoundException e) {
            assumeTrue("looma-core is not compiled", false);
            return;
        }
        Map<String, String> engine = new TreeMap<>();
        for (Object d : (List<?>) builtins.getMethod("all").invoke(null)) {
            String name = (String) call(d, "name");
            engine.put(name, describe(String.valueOf(call(d, "syntax")), (Map<?, ?>) call(d, "tags")));
        }
        Map<String, String> plugin = new TreeMap<>();
        for (LoomaDirectives.Directive d : LoomaDirectives.BUILTIN.all()) {
            if (d.name().equals(LoomaDirectives.VERBATIM)) continue; // handled by the engine's lexer
            Map<String, LoomaDirectives.Syntax> tags = new TreeMap<>(d.middle());
            tags.putAll(d.ends());
            plugin.put(d.name(), describe(d.syntax().name(), tags));
        }
        assertTrue("engine directives not found", engine.size() > 20);
        assertEquals(engine, plugin);
    }

    private static Object call(Object target, String method) throws Exception {
        var m = target.getClass().getMethod(method);
        m.setAccessible(true);
        Object value = m.invoke(target);
        assertNotNull(method, value);
        return value;
    }

    private static String describe(String syntax, Map<?, ?> tags) {
        Map<String, String> sorted = new TreeMap<>();
        tags.forEach((k, v) -> sorted.put(String.valueOf(k), String.valueOf(v)));
        return syntax + " " + sorted;
    }
}
