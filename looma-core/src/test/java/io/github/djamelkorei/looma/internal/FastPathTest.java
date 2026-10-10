package io.github.djamelkorei.looma.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.spi.Expr;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The fast path must give exactly what SpEL gives (same value, or a failure in both). */
class FastPathTest {

    public static class Bean {
        public String getName() { return "n"; }
        public boolean isActive() { return true; }
        public String isWeird() { return "not boolean"; }      // is-prefix on non-boolean: not a getter
        public Bean getChild() { return null; }
        public String getBoom() { throw new IllegalStateException("boom"); }
        public Map<String, Object> getMap() { return Map.of("k", "v"); }
        public Inner getInner() { return new Inner(); }
    }

    public record Rec(String title) {}

    private static class Inner implements Named { public String getName() { return "inner"; } } // non-public class
    public interface Named { String getName(); }

    private static Scope scope() {
        Scope s = new Scope(null);
        Map<String, Object> m = new HashMap<>();
        m.put("present", "yes");
        m.put("nullValue", null);
        s.put("bean", new Bean());
        s.put("map", m);
        s.put("rec", new Rec("t"));
        s.put("flag", Boolean.TRUE);
        s.put("text", "abc");
        s.put("thread", Thread.currentThread());
        s.put("list", List.of(1, 2));
        return s;
    }

    private static Object result(Expr expr, Scope scope) {
        try {
            return Expressions.eval(expr, scope);
        } catch (LoomaException e) {
            return "ERROR";
        }
    }

    private static void same(String source) {
        Expr fast = Expressions.parse(source, 1);
        Expr slow = new Expr(fast.spel(), fast.source(), fast.line()); // no fast path: pure SpEL
        Object expected = result(slow, scope());
        Object actual = result(fast, scope());
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Objects.deepEquals(expected, actual),
                source + ": SpEL gave " + expected + " but the fast path gave " + actual);
    }

    @Test
    void sameResultsAsSpelForEveryEdgeCase() {
        for (String e : List.of(
                "$bean.name", "bean.active", "$bean->weird", "bean.child", "bean.child.name", "bean.boom",
                "bean.map.k", "bean.map.missing", "map.present", "map.nullValue", "map.absent", "map.empty",
                "bean.inner.name", "rec.title", "missing", "missing.x", "text.length", "text.bytes",
                "thread.name", "bean.class", "bean.class.name", "list.empty",
                "!flag", "!bean.active", "!text", "!missing",
                "flag ? 'a' : 'b'", "bean.active ? \"x\" : \"y\"", "text ? 'a' : 'b'", "missing ? 'a' : 'b'",
                "true", "null", "flag and true", "bean.name.length()")) {
            same(e);
        }
    }

    @Test
    void fastPathIsUsedForTheCommonShapesOnly() {
        assertNotNull(Expressions.parse("$user->name", 1).fastPath());
        assertNotNull(Expressions.parse("!$a.b", 1).fastPath());
        assertNotNull(Expressions.parse("$loop->even ? 'e' : 'o'", 1).fastPath());
        assertNull(Expressions.parse("true", 1).fastPath());
        assertNull(Expressions.parse("a == b", 1).fastPath());
        assertNull(Expressions.parse("a.b()", 1).fastPath());
        assertNull(Expressions.parse("a ?? 'd'", 1).fastPath());
        assertNull(Expressions.parse("x ? 'it''s' : 'b'", 1).fastPath());
    }
}
