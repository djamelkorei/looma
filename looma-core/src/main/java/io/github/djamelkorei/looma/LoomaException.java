package io.github.djamelkorei.looma;

/** Thrown for template syntax and rendering errors. The message names the template and line when known. */
public class LoomaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String detail;
    private final int line;
    private final String template;

    public LoomaException(String message) {
        this(message, 0, null);
    }

    public LoomaException(String message, int line, Throwable cause) {
        this(message, line, null, cause);
    }

    private LoomaException(String detail, int line, String template, Throwable cause) {
        super(compose(detail, line, template), cause);
        this.detail = detail;
        this.line = line;
        this.template = template;
    }

    private static String compose(String detail, int line, String template) {
        if (template == null) {
            return line > 0 ? detail + " (line " + line + ")" : detail;
        }
        return "In template '" + template + "'" + (line > 0 ? " at line " + line : "") + ": " + detail;
    }

    /** Returns this exception tagged with the template it occurred in; the innermost tag wins. */
    public LoomaException inTemplate(String name) {
        return template != null ? this : new LoomaException(detail, line, name, getCause());
    }

    /** 1-based line in the template, or 0 if unknown. */
    public int line() {
        return line;
    }

    /** Name of the template in which the error occurred, or null if unknown. */
    public String template() {
        return template;
    }
}
