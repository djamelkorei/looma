package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemTemplateLoaderTest {

    @TempDir
    Path dir;

    @Test
    void loadsNestedTemplatesByDottedOrSlashedName() throws IOException {
        Files.createDirectories(dir.resolve("admin"));
        Files.writeString(dir.resolve("admin/users.looma.html"), "Users: {{ $n }}");
        TemplateLoader loader = new FileSystemTemplateLoader(dir, ".looma.html");
        assertTrue(loader.exists("admin.users"));
        assertTrue(loader.exists("admin/users"));
        assertFalse(loader.exists("admin.nobody"));
        assertEquals("Users: 3", new LoomaEngine(loader, true).render("admin.users", Map.of("n", 3)));
        assertThrows(LoomaException.class, () -> loader.load("nope"));
    }

    @Test
    void cannotEscapeTheRootFolder() throws IOException {
        Path outside = Files.writeString(dir.resolveSibling("secret-" + System.nanoTime() + ".looma.html"), "secret");
        Path root = Files.createDirectories(dir.resolve("templates"));
        TemplateLoader loader = new FileSystemTemplateLoader(root, ".looma.html");
        String name = "../" + outside.getFileName().toString().replace(".looma.html", "");
        assertFalse(loader.exists(name));
        assertFalse(loader.exists("/etc/passwd"));
        assertFalse(loader.exists(null));
        assertThrows(LoomaException.class, () -> loader.load(name));
        Files.deleteIfExists(outside);
    }

    @Test
    void editsAreVisibleImmediatelyWhenCachingIsOff() throws IOException {
        Path file = dir.resolve("page.looma.html");
        Files.writeString(file, "v1");
        LoomaEngine live = LoomaEngine.builder(new FileSystemTemplateLoader(dir, ".looma.html")).cache(false).build();
        assertEquals("v1", live.render("page", Map.of()));
        Files.writeString(file, "v2 @if(true)edited@endif");
        assertEquals("v2 edited", live.render("page", Map.of()));
    }
}
