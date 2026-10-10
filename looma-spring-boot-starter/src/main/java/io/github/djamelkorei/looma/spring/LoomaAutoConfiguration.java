package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.ClasspathTemplateLoader;
import io.github.djamelkorei.looma.FileSystemTemplateLoader;
import io.github.djamelkorei.looma.LoomaEngine;
import io.github.djamelkorei.looma.TemplateLoader;
import io.github.djamelkorei.looma.spi.Directive;
import org.springframework.beans.factory.ObjectProvider;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration
@ConditionalOnClass(LoomaEngine.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LoomaProperties.class)
public class LoomaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TemplateLoader loomaTemplateLoader(LoomaProperties props, ResourceLoader resourceLoader) {
        if (props.getTemplateDir() != null) {
            return new FileSystemTemplateLoader(Path.of(props.getTemplateDir()), props.getSuffix());
        }
        return new ClasspathTemplateLoader(props.getPrefix(), props.getSuffix(), resourceLoader.getClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean
    public LoomaEngine loomaEngine(TemplateLoader loader, LoomaProperties props, ObjectProvider<Directive> directives) {
        // Every Directive bean is a custom directive; no further registration is needed.
        return LoomaEngine.builder(loader)
                .cache(props.isCache())
                .directives(directives.orderedStream().toList())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "looma", name = "check-templates", havingValue = "true", matchIfMissing = true)
    public LoomaTemplateChecker loomaTemplateChecker(LoomaEngine engine, LoomaProperties props) {
        return new LoomaTemplateChecker(engine, props.getPrefix(), props.getSuffix(), props.getTemplateDir());
    }

    @Bean
    @ConditionalOnMissingBean(name = "loomaViewResolver")
    public LoomaViewResolver loomaViewResolver(LoomaEngine engine, LoomaProperties props,
            ApplicationContext context) {
        // The ApplicationContext delegates to the 'messageSource' bean (Boot's spring.messages.* support).
        return new LoomaViewResolver(engine, props.getContentType(), context);
    }
}
