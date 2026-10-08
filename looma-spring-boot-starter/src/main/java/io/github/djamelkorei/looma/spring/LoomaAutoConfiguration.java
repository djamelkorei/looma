package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.ClasspathTemplateLoader;
import io.github.djamelkorei.looma.LoomaEngine;
import io.github.djamelkorei.looma.TemplateLoader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
        return new ClasspathTemplateLoader(props.getPrefix(), props.getSuffix(), resourceLoader.getClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean
    public LoomaEngine loomaEngine(TemplateLoader loader, LoomaProperties props) {
        return new LoomaEngine(loader, props.isCache());
    }

    @Bean
    @ConditionalOnMissingBean(name = "loomaViewResolver")
    public LoomaViewResolver loomaViewResolver(LoomaEngine engine, LoomaProperties props,
            ApplicationContext context) {
        // The ApplicationContext delegates to the 'messageSource' bean (Boot's spring.messages.* support).
        return new LoomaViewResolver(engine, props.getContentType(), context);
    }
}
