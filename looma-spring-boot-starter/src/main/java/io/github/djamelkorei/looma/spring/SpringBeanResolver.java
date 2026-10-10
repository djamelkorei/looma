package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.spi.TemplateAccessible;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.context.ApplicationContext;

/** Resolves {@code @name} in templates to Spring beans, enforcing {@code looma.beans.*}. */
final class SpringBeanResolver implements Function<String, Object> {

    private final ApplicationContext context;
    private final LoomaProperties.Beans.Access access;
    private final Set<String> allowed;
    private final Map<String, Boolean> decisions = new ConcurrentHashMap<>();

    SpringBeanResolver(ApplicationContext context, LoomaProperties.Beans config) {
        this.context = context;
        this.access = config.getAccess();
        this.allowed = Set.copyOf(config.getAllowed());
    }

    /**
     * Beans whose name ends with the requested one, e.g. "sampleApp.ProjectService" for @projectService: Spring
     * names a nested class after its outer class too.
     */
    private String suggestion(String name) {
        String wanted = name.toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> close = java.util.Arrays.stream(context.getBeanDefinitionNames())
                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).endsWith(wanted)).limit(3).toList();
        if (close.isEmpty()) return "";
        return ". Did you mean " + String.join(", ", close.stream().map(n -> "'" + n + "'").toList())
                + "? Give the bean a plain name, e.g. @Service(\"" + name + "\")";
    }

    @Override
    public Object apply(String name) {
        if (access == LoomaProperties.Beans.Access.NONE) {
            throw new IllegalStateException("Bean access from templates is disabled (looma.beans.access=none)");
        }
        if (!context.containsBean(name)) {
            throw new IllegalStateException("No bean named '" + name + "' (used as @" + name + ")" + suggestion(name));
        }
        boolean ok = decisions.computeIfAbsent(name, n -> access == LoomaProperties.Beans.Access.ALL
                || allowed.contains(n)
                || context.findAnnotationOnBean(n, TemplateAccessible.class) != null);
        if (!ok) {
            throw new IllegalStateException("Bean '" + name + "' is not accessible from templates. Annotate its class "
                    + "with @TemplateAccessible or add it to looma.beans.allowed.");
        }
        return context.getBean(name);
    }
}
