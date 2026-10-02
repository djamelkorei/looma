package io.github.djamelkorei.looma.internal;

/** Public key/value holder so {@code @foreach(map as e)} can use {@code e.key} / {@code e.value}. */
public final class MapEntry {

    private final Object key;
    private final Object value;

    MapEntry(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    public Object getKey() { return key; }
    public Object getValue() { return value; }
}
