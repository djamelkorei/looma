package io.github.djamelkorei.looma.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean whose public methods templates may call as {@code @beanName.method(...)}, like
 * Thymeleaf's {@code ${@bean.method()}}. Only marked beans (or ones listed in {@code looma.beans.allowed}) are
 * reachable, so a template cannot reach your repositories, data sources or security beans by accident.
 *
 * <pre>{@code
 * @Service
 * @TemplateAccessible
 * public class PriceService {
 *     public String format(BigDecimal amount) { ... }
 * }
 * }</pre>
 * Template: {@code {{ @priceService.format($order.total) }}}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TemplateAccessible {
}
