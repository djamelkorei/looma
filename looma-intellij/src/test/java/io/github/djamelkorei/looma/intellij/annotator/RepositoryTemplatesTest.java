package io.github.djamelkorei.looma.intellij.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** The templates of this repository's demo and tests must show no errors. */
public class RepositoryTemplatesTest extends BasePlatformTestCase {

    private static final List<String> FOLDERS = List.of(
            "../looma-demo/src/main/resources/templates",
            "../looma-it/src/main/resources/templates",
            "../looma-spring-boot-starter/src/test/resources/templates");

    public void testNoErrors() throws IOException {
        int checked = 0;
        for (String folder : FOLDERS) {
            Path root = Path.of(folder);
            if (!Files.isDirectory(root)) continue;
            String module = root.getName(1).toString();
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(p -> p.toString().endsWith(".looma.html")).toList();
            }
            List<PsiFile> added = new java.util.ArrayList<>();
            for (Path p : files) {
                added.add(myFixture.addFileToProject(module + "/templates/" + root.relativize(p), Files.readString(p)));
            }
            for (int i = 0; i < files.size(); i++) {
                Path p = files.get(i);
                myFixture.configureFromExistingVirtualFile(added.get(i).getVirtualFile());
                List<String> errors = myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
                        .map(HighlightInfo::getDescription).toList();
                assertEmpty(p + ": " + errors, errors);
                checked++;
            }
        }
        assertTrue("no templates found", checked > 0);
    }
}
