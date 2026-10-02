package io.github.djamelkorei.looma;

/** Thrown for template syntax and rendering errors. */
public class LoomaException extends RuntimeException {

    private final int line;

    public LoomaException(String message) {
        this(message, 0, null);
    }

    public LoomaException(String message, int line, Throwable cause) {
        super(line > 0 ? message + " (line " + line + ")" : message, cause);
        this.line = line;
    }

    /** 1-based line in the template, or 0 if unknown. */
    public int line() {
        return line;
    }
}
