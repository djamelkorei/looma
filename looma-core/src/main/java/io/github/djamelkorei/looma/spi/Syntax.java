package io.github.djamelkorei.looma.spi;

/** How a directive is written in a template. */
public enum Syntax {
    /** {@code @name}: no argument list. */
    NONE,
    /** {@code @name} or {@code @name(args)}; the '(' must directly follow the name. */
    OPTIONAL,
    /** {@code @name(args)}; whitespace before '(' is allowed. Without '(' the text is left alone. */
    REQUIRED
}
