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

    @Override
    public Object apply(String name) {
        if (access == LoomaProperties.Beans.Access.NONE) {
            throw new IllegalStateException("Bean access from templates is disabled (looma.beans.access=none)");
        }
        if (!context.containsBean(name)) {
            throw new IllegalStateException("No bean named '" + name + "' (used as @" + name + ")");
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
