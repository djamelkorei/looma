package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.Htmlable;

/** Pre-rendered, already-escaped markup such as a component slot. */
record SafeHtml(String html) implements Htmlable {

    @Override
    public String toHtml() {
        return html;
    }

    @Override
    public String toString() {
        return html;
    }
}
