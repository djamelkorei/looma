package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.Htmlable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** The {@code attributes} bag of a component. {@code {{ attributes }}} renders {@code a="b" c="d"}. */
public final class ComponentAttributes implements Htmlable {

    private final Map<String, Object> values;

    ComponentAttributes(Map<String, Object> values) {
        this.values = values;
    }

    public Object get(String name) {
        return values.get(name);
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    /** Copy without the given comma-separated attribute names. */
    public ComponentAttributes except(String names) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        names(names).forEach(copy::remove);
        return new ComponentAttributes(copy);
    }

    /** Copy with only the given comma-separated attribute names. */
    public ComponentAttributes only(String names) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.keySet().retainAll(names(names));
        return new ComponentAttributes(copy);
    }

    /** Merges defaults under the given attributes; {@code class} values are concatenated, others overridden. */
    public ComponentAttributes merge(Map<?, ?> defaults) {
        Map<String, Object> merged = new LinkedHashMap<>();
        defaults.forEach((k, v) -> merged.put(String.valueOf(k), v));
        values.forEach((k, v) -> {
            if (k.equals("class") && merged.get("class") != null) {
                merged.put(k, merged.get("class") + " " + v);
            } else {
                merged.put(k, v);
            }
        });
        return new ComponentAttributes(merged);
    }

    @Override
    public String toHtml() {
        StringBuilder sb = new StringBuilder();
        values.forEach((k, v) -> {
            if (v == null || Boolean.FALSE.equals(v)) return;
            if (sb.length() > 0) sb.append(' ');
            Html.escape(k, sb);
            if (!Boolean.TRUE.equals(v)) {
                sb.append("=\"");
                Html.escape(String.valueOf(v), sb);
                sb.append('"');
            }
        });
        return sb.toString();
    }

    @Override
    public String toString() {
        return toHtml();
    }

    private static java.util.List<String> names(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}
