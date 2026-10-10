package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the dependency rules that keep the engine easy to change:
 * <pre>
 *   spi        depends on nothing of ours (it is the public contract)
 *   internal   (lexer, parser, renderer) knows no directive: it may use spi only
 *   directive  built-in directives: they may use spi only, exactly like a user's directive would
 * </pre>
 */
class ArchitectureTest {

    private static final Path SOURCES = Path.of("src/main/java/io/github/djamelkorei/looma");

    @Test
    void spiDependsOnNoOtherLoomaPackage() throws IOException {
        assertNoImports("spi", List.of("io.github.djamelkorei.looma.internal", "io.github.djamelkorei.looma.directive"));
    }

    @Test
    void engineCoreKnowsNoDirective() throws IOException {
        assertNoImports("internal", List.of("io.github.djamelkorei.looma.directive"));
    }

    @Test
    void builtInDirectivesUseOnlyThePublicSpi() throws IOException {
        // The same API a third-party directive gets; if a built-in needs more, the SPI is missing something.
        assertNoImports("directive", List.of("io.github.djamelkorei.looma.internal"));
    }

    private static void assertNoImports(String pkg, List<String> forbidden) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES.resolve(pkg))) {
            List<String> violations = files.filter(f -> f.toString().endsWith(".java"))
                    .flatMap(f -> lines(f).filter(l -> l.startsWith("import "))
                            .filter(l -> forbidden.stream().anyMatch(l::contains))
                            .map(l -> f.getFileName() + ": " + l))
                    .collect(Collectors.toList());
            assertTrue(violations.isEmpty(), "dependency rule broken in '" + pkg + "': " + violations);
        }
    }

    private static Stream<String> lines(Path f) {
        try {
            return Files.readAllLines(f).stream();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
