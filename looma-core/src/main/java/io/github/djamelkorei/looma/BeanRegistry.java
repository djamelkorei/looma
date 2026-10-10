package io.github.djamelkorei.looma;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * What {@code @name} in an expression resolves to (templates see it as the {@code _beans} variable).
 * Looks beans up on use and never lists them; the resolver decides which are allowed and explains refusals.
 */
public final class BeanRegistry extends AbstractMap<String, Object> {

    private static final BeanRegistry NONE = new BeanRegistry(name -> {
        throw new IllegalStateException("Bean references like @" + name + " need a bean resolver; "
                + "the Spring Boot starter provides one, or use LoomaEngine.builder(...).beans(...)");
    });

    private final Function<String, Object> resolver;

    /** @param resolver returns the bean, or throws an exception whose message tells the developer what to do */
    public BeanRegistry(Function<String, Object> resolver) {
        this.resolver = resolver;
    }

    static BeanRegistry none() {
        return NONE;
    }

    @Override
    public boolean containsKey(Object name) {
        return true; // every @name goes to the resolver, which explains a refusal better than "not found"
    }

    @Override
    public Object get(Object name) {
        return resolver.apply(String.valueOf(name));
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return Set.of(); // beans are never enumerated
    }

    @Override
    public String toString() {
        return "beans";
    }
}
