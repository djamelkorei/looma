package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.LoomaEngine;
import io.github.djamelkorei.looma.LoomaException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Parses every template on the classpath at startup and fails with all syntax errors at once, so a typo
 * is found when the application boots (or in CI) and not by the first user who opens that page.
 */
public class LoomaTemplateChecker implements SmartInitializingSingleton {

    private final LoomaEngine engine;
    private final String prefix;
    private final String suffix;

    public LoomaTemplateChecker(LoomaEngine engine, String prefix, String suffix) {
        this.engine = engine;
        this.prefix = prefix.isEmpty() || prefix.endsWith("/") ? prefix : prefix + "/";
        this.suffix = suffix;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<LoomaException> errors = engine.check(templateNames());
        if (!errors.isEmpty()) {
            throw new IllegalStateException(errors.size() + " Looma template(s) have syntax errors:\n"
                    + errors.stream().map(e -> " - " + e.getMessage()).collect(Collectors.joining("\n"))
                    + "\n(disable this check with looma.check-templates=false)");
        }
    }

    /** Logical names (as used by controllers) of all templates found under the prefix. */
    List<String> templateNames() {
        List<String> names = new ArrayList<>();
        try {
            for (Resource r : new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:" + prefix + "**/*" + suffix)) {
                String url = r.getURL().toString();
                int at = url.lastIndexOf(prefix);
                if (at >= 0 && url.endsWith(suffix)) {
                    names.add(url.substring(at + prefix.length(), url.length() - suffix.length()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list Looma templates under " + prefix, e);
        }
        return names;
    }
}
