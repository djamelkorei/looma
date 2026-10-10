package io.github.djamelkorei.looma.helper;

import io.github.djamelkorei.looma.spi.TemplateHelper;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * {@code List} in templates: null-safe collection functions. Accepts lists, sets, arrays and maps (values).
 * Never modifies what you pass in.
 *
 * <pre>
 * {{ List.size($cart) }} items, total {{ Number.currency(List.sum(List.pluck($cart, 'price')), 'EUR') }}
 * &#64;foreach(List.chunk($products, 3) as $row) &lt;div class="row"&gt;...&lt;/div&gt; @endforeach
 * &#64;foreach(List.groupBy($people, 'team') as $team) {{ $team.key }}: {{ $team.value.size() }} @endforeach
 * &#64;foreach(List.sortBy($users, 'name') as $u) ... @endforeach
 * </pre>
 */
public final class ListHelper implements TemplateHelper {

    @Override
    public String name() {
        return "List";
    }

    public int size(Object items) {
        if (items == null) return 0;
        if (items instanceof Collection<?> c) return c.size();
        if (items instanceof Map<?, ?> m) return m.size();
        if (items instanceof CharSequence s) return s.length();
        if (items.getClass().isArray()) return Array.getLength(items);
        return list(items).size();
    }

    public boolean isEmpty(Object items) {
        return size(items) == 0;
    }

    public Object first(Object items) {
        List<Object> l = list(items);
        return l.isEmpty() ? null : l.get(0);
    }

    public Object last(Object items) {
        List<Object> l = list(items);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    public boolean contains(Object items, Object item) {
        return list(items).contains(item);
    }

    /** Natural order (numbers, text, dates...); nulls last. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Object> sort(Object items) {
        List<Object> copy = new ArrayList<>(list(items));
        copy.sort((Comparator) Comparator.nullsLast(Comparator.naturalOrder()));
        return copy;
    }

    /** Sorted by a property (getter or map key); nulls last. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Object> sortBy(Object items, String property) {
        List<Object> copy = new ArrayList<>(list(items));
        Comparator<Object> byValue = (Comparator) Comparator.nullsLast(Comparator.naturalOrder());
        copy.sort(Comparator.comparing(o -> Properties.read(o, property), byValue));
        return copy;
    }

    /** {@link #sortBy} in descending order. */
    public List<Object> sortByDesc(Object items, String property) {
        List<Object> sorted = sortBy(items, property);
        Collections.reverse(sorted);
        return sorted;
    }

    public List<Object> reverse(Object items) {
        List<Object> copy = new ArrayList<>(list(items));
        Collections.reverse(copy);
        return copy;
    }

    /** The first {@code n} items. */
    public List<Object> take(Object items, int n) {
        List<Object> l = list(items);
        return new ArrayList<>(l.subList(0, Math.max(0, Math.min(n, l.size()))));
    }

    /** All but the first {@code n} items. */
    public List<Object> skip(Object items, int n) {
        List<Object> l = list(items);
        return new ArrayList<>(l.subList(Math.max(0, Math.min(n, l.size())), l.size()));
    }

    /** Splits into rows of {@code size}: perfect for grids. */
    public List<List<Object>> chunk(Object items, int size) {
        if (size < 1) throw new IllegalArgumentException("List.chunk: size must be at least 1");
        List<Object> l = list(items);
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < l.size(); i += size) rows.add(new ArrayList<>(l.subList(i, Math.min(i + size, l.size()))));
        return rows;
    }

    public List<Object> distinct(Object items) {
        return new ArrayList<>(new LinkedHashSet<>(list(items)));
    }

    /** One property of every item: pluck($orders, 'total'). */
    public List<Object> pluck(Object items, String property) {
        List<Object> values = new ArrayList<>();
        for (Object o : list(items)) values.add(Properties.read(o, property));
        return values;
    }

    /** Groups by a property, keeping first-seen order: iterate it with {@code $group.key} / {@code $group.value}. */
    public Map<Object, List<Object>> groupBy(Object items, String property) {
        Map<Object, List<Object>> groups = new LinkedHashMap<>();
        for (Object o : list(items)) groups.computeIfAbsent(Properties.read(o, property), k -> new ArrayList<>()).add(o);
        return groups;
    }

    /** Sum of numbers (nulls ignored); whole numbers stay whole, decimals are exact. */
    public Number sum(Object items) {
        BigDecimal total = BigDecimal.ZERO;
        boolean whole = true;
        for (Object o : list(items)) {
            if (o == null) continue;
            Number n = (Number) o;
            whole &= n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
            total = total.add(n instanceof BigDecimal b ? b : new BigDecimal(n.toString()));
        }
        return whole ? (Number) total.longValueExact() : total;
    }

    /** Average of numbers (nulls ignored), or null for an empty list. */
    public Double avg(Object items) {
        double total = 0;
        int count = 0;
        for (Object o : list(items)) {
            if (o == null) continue;
            total += ((Number) o).doubleValue();
            count++;
        }
        return count == 0 ? null : total / count;
    }

    public Object min(Object items) {
        List<Object> sorted = sort(items);
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    public Object max(Object items) {
        List<Object> sorted = sort(items);
        for (int i = sorted.size() - 1; i >= 0; i--) if (sorted.get(i) != null) return sorted.get(i);
        return null;
    }

    private static List<Object> list(Object items) {
        List<Object> out = new ArrayList<>();
        if (items == null) return out;
        if (items instanceof Collection<?> c) out.addAll(c);
        else if (items instanceof Map<?, ?> m) out.addAll(m.values());
        else if (items.getClass().isArray()) for (int i = 0; i < Array.getLength(items); i++) out.add(Array.get(items, i));
        else if (items instanceof Iterable<?> it) it.forEach(out::add);
        else out.add(items);
        return out;
    }
}
