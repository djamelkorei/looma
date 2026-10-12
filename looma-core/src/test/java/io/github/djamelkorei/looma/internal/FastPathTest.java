package io.github.djamelkorei.looma.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.helper.DateHelper;
import io.github.djamelkorei.looma.helper.ListHelper;
import io.github.djamelkorei.looma.helper.MathHelper;
import io.github.djamelkorei.looma.helper.NumberHelper;
import io.github.djamelkorei.looma.helper.StringHelper;
import io.github.djamelkorei.looma.spi.Expr;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * The direct evaluator must give exactly what SpEL gives: the same value (and type), or a failure in both. Every
 * expression is evaluated twice, once with the fast path and once with SpEL alone, against the same data.
 */
class FastPathTest {

    public static class Bean {
        public String getName() { return "n"; }
        public boolean isActive() { return true; }
        public String isWeird() { return "not boolean"; }      // is-prefix on non-boolean: not a getter
        public Bean getChild() { return null; }
        public String getBoom() { throw new IllegalStateException("boom"); }
        public Map<String, Object> getMap() { return Map.of("k", "v"); }
        public Inner getInner() { return new Inner(); }
        public String getxCoord() { return "lower x"; }        // SpEL tries getxCoord before getXCoord
        public String getXCoord() { return "upper X"; }
        public String getURL() { return "url"; }
        public String size() { return "record-style"; }
        public static String getStatic() { return "static getter"; }
        public void getNothing() {}
        public String field = "public field";

        public String over(String s) { return "string"; }
        public String over(Object o) { return "object"; }
        public String over(int i) { return "int"; }
        public String over(long l) { return "long"; }
        public String one(CharSequence s) { return "cs:" + s; }
        public String num(Number n) { return "num:" + n + ":" + n.getClass().getSimpleName(); }
        public String prim(int i) { return "int:" + i; }
        public String prim2(long l) { return "long:" + l; }
        public String dbl(double d) { return "double:" + d; }
        public String var(String... s) { return "varargs:" + s.length; }
        public String coll(Collection<Object> c) { return "coll:" + c.size() + ":" + c.getClass().getSimpleName(); }
        public String list(List<String> l) { return "list:" + l; }
        public String obj(Object o) { return "obj:" + o; }
        public String nul(String s) { return "nul:" + s; }
        public String two(String a, Integer b) { return a + b; }
        public String fail() { throw new IllegalStateException("fail"); }
        public Bean self() { return this; }
        public Object nothing() { return null; }
    }

    public record Rec(String title, int count) {}

    public enum Color { RED, GREEN }

    public enum Size { SMALL }

    private static class Inner implements Named { public String getName() { return "inner"; } } // non-public class

    public interface Named { String getName(); }

    private static Scope scope() {
        Scope s = new Scope(null);
        Map<String, Object> m = new HashMap<>();
        m.put("present", "yes");
        m.put("nullValue", null);
        m.put("n", 3);
        s.put("bean", new Bean());
        s.put("map", m);
        s.put("rec", new Rec("t", 4));
        s.put("flag", Boolean.TRUE);
        s.put("off", Boolean.FALSE);
        s.put("text", "abc");
        s.put("empty", "");
        s.put("sb", new StringBuilder("abc"));
        s.put("thread", Thread.currentThread());
        s.put("list", List.of(1, 2));
        s.put("mlist", new ArrayList<>(List.of(1, 1, 2)));
        s.put("set", new LinkedHashSet<>(List.of("a", "b")));
        s.put("lmap", new LinkedHashMap<>(Map.of("k", 1)));
        s.put("i", 7);
        s.put("zero", 0);
        s.put("neg", -3);
        s.put("l", 7L);
        s.put("d", 2.5d);
        s.put("f", 1.5f);
        s.put("sh", (short) 3);
        s.put("by", (byte) 2);
        s.put("bd", new BigDecimal("10.50"));
        s.put("bd2", new BigDecimal("3"));
        s.put("bi", new BigInteger("12345678901234567890"));
        s.put("ai", new AtomicInteger(5));
        s.put("al", new AtomicLong(5));
        s.put("nan", Double.NaN);
        s.put("big", Integer.MAX_VALUE);
        s.put("red", Color.RED);
        s.put("green", Color.GREEN);
        s.put("small", Size.SMALL);
        s.put("ch", 'x');
        s.put("date", LocalDate.of(2026, 1, 2));
        s.put("dt", LocalDateTime.of(2026, 1, 2, 3, 4));
        s.put("nothing", null);
        s.put("String", new StringHelper());
        s.put("Number", new NumberHelper());
        s.put("List", new ListHelper());
        s.put("Math", new MathHelper());
        s.put("Date", new DateHelper());
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
        boolean sameType = expected == null || actual == null || expected.getClass() == actual.getClass()
                || expected instanceof CharSequence; // e.g. SafeHtml from nl2br compares by text below
        assertTrue(sameType && Objects.deepEquals(text(expected), text(actual)),
                source + ": SpEL gave " + describe(expected) + " but the fast path gave " + describe(actual));
    }

    private static Object text(Object v) {
        if (v instanceof io.github.djamelkorei.looma.Htmlable h) return h.toHtml();
        return v instanceof CharSequence ? v.toString() : v;
    }

    private static String describe(Object v) {
        return v == null ? "null" : v + " (" + v.getClass().getSimpleName() + ")";
    }

    @Test
    void sameResultsAsSpelForEveryEdgeCase() {
        for (String e : List.of(
                // properties: maps, getters, records, the sandbox
                "$bean.name", "bean.active", "$bean->weird", "bean.child", "bean.child.name", "bean.boom",
                "bean.map.k", "bean.map.missing", "map.present", "map.nullValue", "map.absent", "map.empty",
                "bean.inner.name", "rec.title", "rec.count", "missing", "missing.x", "text.length", "text.bytes",
                "thread.name", "bean.class", "bean.class.name", "list.empty", "list.size", "bean.xCoord",
                "bean.URL", "bean.uRL", "bean.size", "bean.static", "bean.nothing", "bean.field", "bean.Name",
                "lmap.k", "set.empty", "red.name", "red.ordinal", "rec.title.length()",
                // literals
                "true", "TRUE", "False", "null", "NULL", "42", "42L", "0.125", "'it''s'", "\"say \"\"hi\"\"\"", "'a\"b'",
                "-0.04", "-7", "+7", "+text", "-text", "--7", "- -7",
                // boolean logic
                "!flag", "!bean.active", "!text", "!missing", "not flag", "!!flag", "flag and true", "flag && off",
                "off || flag", "off or off", "flag and text", "text and flag", "off and text", "flag or text",
                "flag ? 'a' : 'b'", "bean.active ? \"x\" : \"y\"", "text ? 'a' : 'b'", "missing ? 'a' : 'b'",
                "off ? 1 : flag ? 2 : 3", "flag ? missing.x : 1", "(flag)", "((i))",
                // calls
                "bean.name.length()", "text.toUpperCase()", "text.substring(1)", "text.substring(1, 2)",
                "text.substring(i)", "text.charAt(0)", "red.name().toLowerCase()", "list.size()", "list.get(0)",
                "list.contains(1)", "list.contains(l)", "map.size()", "map.get('n')", "lmap.get('k')",
                "bean.over('s')", "bean.over(i)", "bean.over(l)", "bean.over(rec)", "bean.over(nothing)",
                "bean.one(text)", "bean.one(sb)", "bean.one(i)", "bean.num(i)", "bean.num(bd)", "bean.num(d)",
                "bean.prim(i)", "bean.prim(sh)", "bean.prim(l)", "bean.prim(nothing)", "bean.prim2(i)", "bean.prim2(l)",
                "bean.dbl(d)", "bean.dbl(i)", "bean.dbl(f)", "bean.var()", "bean.var('a', 'b')", "bean.coll(mlist)",
                "bean.coll(set)", "bean.list(list)", "bean.obj(list)", "bean.obj(map)", "bean.obj(nothing)",
                "bean.nul(nothing)", "bean.two('a', 1)", "bean.two('a', l)", "bean.fail()", "bean.self().name",
                "bean.nothing()", "bean.nothing().x", "bean.getClass()", "bean.toString()", "bean.hashCode()",
                "bean.equals(bean)", "text.equals('abc')", "thread.getName()", "text.getClass()",
                "red.compareTo(green)", "text.compareTo('abd')", "bean.missingMethod()", "nothing.size()",
                "nothing?.size()", "nothing?.x", "bean?.name", "text?.length()",
                // helpers
                "String.abbreviate(text, 2)", "String.plural(i, 'task', 'tasks')", "String.slug('Hello World')",
                "String.initials('ada lovelace')", "String.nl2br('a\nb')", "String.join(',', list)",
                "Number.currency(bd, 'EUR')", "Number.compact(bi)", "Number.ordinal(i)", "Number.percent(0.125, 1)",
                "List.sum(list)", "List.sortBy(list, 'x')", "List.first(list)", "List.size(list)", "List.size(nothing)",
                "Math.max(i, l)", "Math.max(i, 3)", "Math.abs(neg)", "Date.format(date, 'yyyy-MM-dd')",
                "Date.daysBetween(date, dt)", "String.format('%s-%s', text, i)", "String.abbreviate(nothing, 2)",
                // arithmetic
                "i + 1", "i - 10", "i * 3", "i / 2", "i % 4", "i / zero", "i % zero", "l / zero", "d / zero",
                "bd / zero", "bd / bd2", "bd2 / bd", "bd + i", "bd * d", "bd % bd2", "bi + i", "bi / i", "bi % i",
                "d + i", "f + i", "f * d", "l + i", "sh + by", "sh * sh", "by - by", "ai + 1", "ai + al", "al * 2",
                "big + 1", "i / 2.0", "1 + 2 * 3", "(1 + 2) * 3", "10 - 2 - 3", "20 / 2 / 5", "-i", "-l", "-d",
                "-f", "-bd", "-bi", "-sh", "-by", "-ai", "nan + 1",
                // concatenation
                "'a' + 'b'", "'n' + i", "i + 'n'", "'n' + l", "'n' + d", "'n' + f", "'n' + bd", "'n' + bi",
                "'n' + flag", "'n' + ch", "'n' + nothing", "nothing + 'n'", "'n' + red", "'n' + date", "'n' + list",
                "'n' + sb", "sb + 'n'", "'project-' + rec.count", "text + text + i", "i + i + 'x'", "'x' + i + i",
                "text - 1", "'b' - 1", "text * 2", "list + 1", "flag + flag", "nothing + nothing",
                // equality
                "i == 7", "i == 7L", "i == 7.0", "l == i", "d == 2.5", "f == 1.5", "bd == 10.5", "bd == 10.50",
                "bd2 == 3", "bi == bi", "sh == 3", "by == 2", "ai == 5", "ai == al", "nan == nan", "text == 'abc'",
                "sb == 'abc'", "text == sb", "flag == true", "flag == 'true'", "red == red", "red == green",
                "red == small", "red == 'RED'", "nothing == null", "nothing == 0", "0 == nothing", "text == nothing",
                "list == list", "date == date", "i != 8", "text != 'x'", "red != green", "rec == rec", "bean == bean",
                "i == text", "date == dt",
                // ordering
                "i > 3", "i >= 7", "i < 7", "i <= 6", "l > i", "d < i", "f >= d", "bd > i", "bd < 11", "bi > l",
                "sh < by", "ai > 4", "ai < al", "nan < 1", "nan >= nan", "text < 'abd'", "text > sb", "red < green",
                "red > small", "date < date", "nothing < 1", "1 > nothing", "nothing <= nothing", "text > 1",
                "1 < text", "flag > off", "list < list", "rec > rec",
                // elvis, inline lists and maps
                "missing ?: 'd'", "empty ?: 'd'", "text ?: 'd'", "zero ?: 'd'", "off ?: 'd'", "sb ?: 'd'",
                "nothing ?: nothing ?: 'last'", "list ?: 1", "map.absent ?: 'd'", "missing.x ?: 'd'",
                "{1, 2}", "{}", "{:}", "{'a': 1}", "{a: 1}", "{text: 1}", "{'a': i, 'b': text}", "{a: i}",
                "{'a': -1}", "{-1: 'x'}", "{'a': {'b': 1}}", "{'a': {'b': i}}", "{1, {2, 3}}", "{i, text}",
                "{'a': missing.x}", "{'k': 1}.k", "{'k': i}.k", "{'a': 1}.size()", "{'a': i}.size()",
                "{'a': 1}.put('b', 2)", "{'a': i}.put('b', 2)", "{1, 2}.add(3)", "{1, i}.add(3)", "{1, i}.size()",
                "{'done': flag, 'urgent': i >= 4, 'late': !off && i > 3}", "{(text): 1}", "{text.length(): 1}",
                "{'a': 1, 'a': 2}", "'Hello ' + ((text) ?: '')", "'Hello ' + ((nothing) ?: '')",
                // outside the subset: still correct, through SpEL
                "list[0]", "T(java.lang.Math).max(1, 2)",
                "text matches 'a.*'", "i between {1, 9}", "i eq 7", "i div 2", "i mod 2", "list.![#this]",
                "x = 1", "i++", "2 ^ 3", "_beans")) {
            same(e);
        }
    }

    /** Every pair of operand kinds through every operator, compared with SpEL. */
    @Test
    void sameResultsAsSpelForEveryOperandCombination() {
        List<String> operands = List.of("i", "zero", "neg", "l", "d", "f", "sh", "by", "bd", "bd2", "bi", "ai", "al",
                "nan", "big", "text", "empty", "sb", "flag", "off", "red", "small", "ch", "date", "nothing", "list",
                "map", "rec", "1", "0", "2.5", "'x'", "''", "true", "null");
        List<String> operators = List.of("+", "-", "*", "/", "%", "==", "!=", "<", "<=", ">", ">=", "&&", "||");
        for (String a : operands) {
            same("-" + a);
            same("!" + a);
            same(a + " ? 1 : 2");
            for (String op : operators) {
                for (String b : operands) {
                    same(a + " " + op + " " + b);
                }
            }
        }
    }

    @Test
    void theCommonShapesAreCompiled() {
        for (String e : List.of("$user->name", "!$a.b", "$loop->even ? 'e' : 'o'", "true", "a == b", "a.b()",
                "x ? 'it''s' : 'b'", "String.abbreviate($p.name, 24)", "$p.status.name() == 'DONE'",
                "'project-' + $p.id", "$p.tasks.size() > 3", "$loop->index >= 3", "-0.04",
                "$g.value.size() / ($projects.size() * 1.0)", "a ?? 'd'", "{'a': 1}", "{'c': $c, 'n': $loop->iteration}")) {
            assertNotNull(Expressions.parse(e, 1).fastPath(), e);
        }
        for (String e : List.of("a[0]", "list.![x]", "@bean.go()", "T(java.lang.Math).max(1, 2)",
                "a eq b", "a matches 'x'", "a.b('c\\\\d')")) {
            assertNull(Expressions.parse(e, 1).fastPath(), e);
        }
    }

    @Test
    void overloadsSpelResolvesByDistanceAreLeftToSpel() {
        // over(String), over(Object), over(int), over(long): two candidates accept a String, so SpEL decides
        Expr e = Expressions.parse("bean.over('s')", 1);
        assertEquals("string", Expressions.eval(e, scope()));
    }
}
