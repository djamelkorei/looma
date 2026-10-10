package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Number, Date, List and extended String helpers, including locale-aware output. */
class UtilityHelpersTest {

    final Map<String, String> files = new HashMap<>();
    final LoomaEngine engine = new LoomaEngine(new TemplateLoader() {
        public boolean exists(String name) { return files.containsKey(name); }
        public String load(String name) { return files.get(name); }
    }, false);

    String render(String template, Map<String, Object> model, Locale locale) {
        files.put("t", template);
        // CLDR uses non-breaking (and narrow non-breaking) spaces in some locales; normalize for readable asserts
        return engine.render("t", model, locale).replace(' ', ' ').replace(' ', ' ');
    }

    String en(String template, Map<String, Object> model) { return render(template, model, Locale.US); }
    String fr(String template, Map<String, Object> model) { return render(template, model, Locale.FRANCE); }

    @Test
    void numbersFollowTheVisitorsLocale() {
        Map<String, Object> m = Map.of("p", new BigDecimal("1234.5"));
        assertEquals("1,234.50", en("{{ Number.decimal($p, 2) }}", m));
        assertEquals("1 234,50", fr("{{ Number.decimal($p, 2) }}", m));
        assertEquals("$1,234.50", en("{{ Number.currency($p, 'USD') }}", m));
        assertEquals("1 234,50 €", fr("{{ Number.currency($p, 'EUR') }}", m));
        assertEquals("1,234,567", en("{{ Number.integer(1234567) }}", Map.of()));
        assertEquals("26% 25.6%", en("{{ Number.percent(0.256) }} {{ Number.percent(0.256, 1) }}", Map.of()));
        assertEquals("1.2K 3.4M", en("{{ Number.compact(1234) }} {{ Number.compact(3400000) }}", Map.of()));
        // money is usually BigDecimal: the decimals must not be dropped ("1M")
        assertEquals("1.3M 2.5K", en("{{ Number.compact($a) }} {{ Number.compact($b) }}",
                Map.of("a", new BigDecimal("1260500.00"), "b", new java.math.BigInteger("2500"))));
        assertEquals("512 B 1.5 KB 2.0 MB", en("{{ Number.bytes(512) }} {{ Number.bytes(1536) }} {{ Number.bytes(2097152) }}", Map.of()));
        assertEquals("1st 2nd 3rd 4th 11th 22nd 113th", en("{{ Number.ordinal(1) }} {{ Number.ordinal(2) }} {{ Number.ordinal(3) }} "
                + "{{ Number.ordinal(4) }} {{ Number.ordinal(11) }} {{ Number.ordinal(22) }} {{ Number.ordinal(113) }}", Map.of()));
        assertEquals("0,050", fr("{{ Number.format(0.05, '0.000') }}", Map.of()));
        assertEquals("123", en("@foreach(Number.sequence(1, 3) as $i){{ $i }}@endforeach", Map.of()));
        assertEquals("321", en("@foreach(Number.sequence(3, 1) as $i){{ $i }}@endforeach", Map.of()));
        assertEquals("", en("{{ Number.decimal($none, 2) }}", Map.of()));
    }

    @Test
    void datesFormatInTheVisitorsLanguage() {
        Map<String, Object> m = Map.of("d", LocalDate.of(2026, 10, 10), "dt", LocalDateTime.of(2026, 10, 10, 14, 30));
        assertEquals("10 October 2026", en("{{ Date.format($d, 'd MMMM yyyy') }}", m));
        assertEquals("10 octobre 2026", fr("{{ Date.format($d, 'd MMMM yyyy') }}", m));
        assertEquals("Saturday samedi", en("{{ Date.dayName($d) }}", m) + " " + fr("{{ Date.dayName($d) }}", m));
        assertEquals("October", en("{{ Date.monthName($d) }}", m));
        assertEquals("2026-10-10 2026-10-10T14:30", en("{{ Date.iso($d) }} {{ Date.format($dt, \"yyyy-MM-dd'T'HH:mm\") }}", m));
        assertEquals("Oct 10, 2026", en("{{ Date.medium($d) }}", m));
        assertEquals("9", en("{{ Date.daysBetween($d, $later) }}", Map.of("d", LocalDate.of(2026, 10, 1), "later", LocalDate.of(2026, 10, 10))));
    }

    @Test
    void relativeTimesAndComparisons() {
        ZonedDateTime now = ZonedDateTime.now();
        Map<String, Object> m = Map.of("past", now.minusHours(3), "future", now.plusDays(2).plusMinutes(1),
                "recent", now.minusSeconds(10), "today", LocalDate.now(), "yesterday", LocalDate.now().minusDays(1));
        assertEquals("3 hours ago | in 2 days | just now", en("{{ Date.relative($past) }} | {{ Date.relative($future) }} | {{ Date.relative($recent) }}", m));
        assertEquals("true true false true", en("{{ Date.isToday($today) }} {{ Date.isPast($past) }} {{ Date.isPast($future) }} {{ Date.isFuture($future) }}", m));
        assertEquals("false", en("{{ Date.isToday($yesterday) }}", m));
    }

    public record Person(String name, String team, int age) {}

    @Test
    void listFunctionsForViews() {
        List<Person> people = List.of(new Person("Cy", "b", 30), new Person("Ada", "a", 36), new Person("Bo", "b", 25));
        Map<String, Object> m = Map.of("people", people, "nums", List.of(3, 1, 2), "prices", List.of(new BigDecimal("1.10"), new BigDecimal("2.20")));
        assertEquals("3 Cy Bo", en("{{ List.size($people) }} {{ List.first($people).name }} {{ List.last($people).name }}", m));
        assertEquals("Ada,Bo,Cy", en("{{ String.join(',', List.pluck(List.sortBy($people, 'name'), 'name')) }}", m));
        assertEquals("Ada,Cy,Bo", en("{{ String.join(',', List.pluck(List.sortByDesc($people, 'age'), 'name')) }}", m));
        assertEquals("b:2 a:1 ", en("@foreach(List.groupBy($people, 'team') as $g){{ $g.key }}:{{ $g.value.size() }} @endforeach", m));
        assertEquals("[1,2][3]", en("@foreach(List.chunk(List.sort($nums), 2) as $row)[{{ String.join(',', $row) }}]@endforeach", m));
        assertEquals("6 2.0 1 3", en("{{ List.sum($nums) }} {{ List.avg($nums) }} {{ List.min($nums) }} {{ List.max($nums) }}", m));
        assertEquals("3.30", en("{{ List.sum($prices) }}", m));
        assertEquals("3,1 | 2", en("{{ String.join(',', List.take($nums, 2)) }} | {{ String.join(',', List.skip($nums, 2)) }}", m));
        assertEquals("2,1,3", en("{{ String.join(',', List.reverse($nums)) }}", m));
        assertEquals("a,b", en("{{ String.join(',', List.distinct({'a', 'b', 'a'})) }}", m));
        assertEquals("0 true", en("{{ List.size($none) }} {{ List.isEmpty($none) }}", m));
        assertEquals("[3, 1, 2]", en("{{ $nums }}", m), "helpers never modify the caller's list");
    }

    @Test
    void stringExtras() {
        Map<String, Object> m = Map.of("t", "The quick brown fox jumps", "multi", "a <b>\nline two");
        assertEquals("The qu…", en("{{ String.abbreviate($t, 7) }}", m));
        assertEquals("The quick…", en("{{ String.truncateWords($t, 2) }}", m));
        assertEquals("5", en("{{ String.wordCount($t) }}", m));
        assertEquals("hello-world-ca-va", en("{{ String.slug('Héllo, World! Ça va?') }}", m));
        assertEquals("1 item 3 items", en("{{ String.plural(1, 'item', 'items') }} {{ String.plural(3, 'item', 'items') }}", m));
        assertEquals("************4242", en("{{ String.mask('4242424242424242', 4) }}", m));
        assertEquals("AL", en("{{ String.initials('ada lovelace') }}", m));
        assertEquals("Ada ada", en("{{ String.capitalize('ada') }} {{ String.uncapitalize('Ada') }}", m));
        assertEquals("user example.com", en("{{ String.substringBefore('user@example.com', '@') }} {{ String.substringAfter('user@example.com', '@') }}", m));
        assertEquals("n/a n/a x", en("{{ String.defaultIfBlank($none, 'n/a') }} {{ String.defaultIfEmpty('', 'n/a') }} {{ String.defaultIfBlank('x', 'n/a') }}", m));
        assertEquals("a &lt;b&gt;<br>\nline two", en("{{ String.nl2br($multi) }}", m), "nl2br escapes first, so it is safe");
    }

    @Test
    void helperPropertyAccessStaysSandboxed() {
        assertThrows(LoomaException.class, () -> en("{{ List.pluck($x, 'class') }}", Map.of("x", List.of("a"))));
        assertThrows(LoomaException.class, () -> en("{{ List.sortBy($x, 'classLoader') }}", Map.of("x", List.of(String.class, Integer.class))));
    }
}
