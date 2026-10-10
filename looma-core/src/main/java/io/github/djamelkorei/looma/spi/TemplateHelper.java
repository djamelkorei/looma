package io.github.djamelkorei.looma.spi;

/**
 * An object whose public methods every template can call under {@link #name()}, e.g. {@code {{ Price.eur($total) }}}.
 *
 * <pre>{@code
 * @Component
 * public class PriceHelper implements TemplateHelper {
 *     public String name() { return "Price"; }
 *     public String eur(Number amount) { return String.format("%.2f €", amount.doubleValue()); }
 * }
 * }</pre>
 *
 * <p>This is how templates call "static" Java functionality safely: instead of opening {@code T(...)}, which can
 * reach any class (and therefore {@code Runtime.exec}), you expose exactly the methods you choose. The class must
 * be public. A model attribute with the same name takes precedence over a helper.
 */
public interface TemplateHelper {

    /** The name templates use, e.g. {@code "Price"}. Letters, digits and '_' only; must not start with a digit. */
    String name();
}
