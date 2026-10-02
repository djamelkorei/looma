package io.github.djamelkorei.looma;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Loads templates from the classpath: {@code prefix + name (dots become slashes) + suffix}. */
public class ClasspathTemplateLoader implements TemplateLoader {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_\\-./]+");

    private final String prefix;
    private final String suffix;
    private final ClassLoader classLoader;

    public ClasspathTemplateLoader(String prefix, String suffix, ClassLoader classLoader) {
        this.prefix = prefix.isEmpty() || prefix.endsWith("/") ? prefix : prefix + "/";
        this.suffix = suffix;
        this.classLoader = classLoader != null ? classLoader : ClasspathTemplateLoader.class.getClassLoader();
    }

    public ClasspathTemplateLoader() {
        this("templates/", ".looma.html", null);
    }

    @Override
    public boolean exists(String name) {
        return locate(name) != null;
    }

    @Override
    public String load(String name) {
        URL url = locate(name);
        if (url == null) {
            throw new LoomaException("Template not found: " + name);
        }
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LoomaException("Cannot read template " + name + ": " + e.getMessage(), 0, e);
        }
    }

    private URL locate(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches() || name.startsWith("/") || name.contains("..")) {
            return null;
        }
        return classLoader.getResource(prefix + name.replace('.', '/') + suffix);
    }
}
