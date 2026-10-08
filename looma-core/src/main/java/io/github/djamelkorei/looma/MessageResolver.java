package io.github.djamelkorei.looma;

/** Resolves {@code @lang('key', args...)}. Supply one to the model as the {@code _messages} variable. */
@FunctionalInterface
public interface MessageResolver {

    /** @return the message, or {@code key} itself if there is none */
    String resolve(String key, Object[] args);
}
