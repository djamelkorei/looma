package io.github.djamelkorei.looma;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads templates from a folder on disk. With caching off this gives live reload during development:
 * edit a file, refresh the page. Point it at {@code src/main/resources/templates} to skip the rebuild.
 */
public class FileSystemTemplateLoader implements TemplateLoader {

    private final Path root;
    private final String suffix;

    public FileSystemTemplateLoader(Path root, String suffix) {
        this.root = root.toAbsolutePath().normalize();
        this.suffix = suffix;
    }

    public Path root() {
        return root;
    }

    @Override
    public boolean exists(String name) {
        Path file = locate(name);
        return file != null && Files.isRegularFile(file);
    }

    @Override
    public String load(String name) {
        Path file = locate(name);
        if (file == null || !Files.isRegularFile(file)) {
            throw new LoomaException("Template not found: " + name);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LoomaException("Cannot read template " + name + ": " + e.getMessage(), 0, e);
        }
    }

    private Path locate(String name) {
        String path = TemplateNames.toPath(name);
        if (path == null) {
            return null;
        }
        Path file = root.resolve(path + suffix).normalize();
        return file.startsWith(root) ? file : null;
    }
}
