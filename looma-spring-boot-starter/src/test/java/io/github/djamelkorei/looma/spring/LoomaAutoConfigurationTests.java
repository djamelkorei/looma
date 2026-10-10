package io.github.djamelkorei.looma.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.djamelkorei.looma.LoomaEngine;
import io.github.djamelkorei.looma.TemplateLoader;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.View;

class LoomaAutoConfigurationTests {

    record Named(String name) implements java.security.Principal {
        @Override public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    private final WebApplicationContextRunner web = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LoomaAutoConfiguration.class));

    @Test
    void registersEngineLoaderAndResolverInServletApps() {
        web.run(ctx -> {
            assertThat(ctx).hasSingleBean(LoomaEngine.class);
            assertThat(ctx).hasSingleBean(TemplateLoader.class);
            assertThat(ctx).hasSingleBean(LoomaViewResolver.class);
        });
    }

    @Test
    void isInactiveOutsideServletWebApps() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LoomaAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(LoomaEngine.class));
    }

    @Test
    void backsOffWhenApplicationDefinesItsOwnEngine() {
        web.withBean("myEngine", LoomaEngine.class, () -> new LoomaEngine(new io.github.djamelkorei.looma.ClasspathTemplateLoader(), false))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(LoomaEngine.class);
                    assertThat(ctx).hasBean("myEngine");
                });
    }

    @Test
    void bindsPrefixAndSuffixProperties() {
        web.withPropertyValues("looma.prefix=custom/", "looma.suffix=.tpl").run(ctx ->
                assertThat(ctx.getBean(LoomaEngine.class).render("hi", Map.of("x", 7))).isEqualTo("custom 7"));
    }

    @Test
    void resolverOnlyHandlesExistingTemplates() {
        web.run(ctx -> {
            LoomaViewResolver resolver = ctx.getBean(LoomaViewResolver.class);
            assertThat(resolver.resolveViewName("hello", null)).isNotNull();
            assertThat(resolver.resolveViewName("nope", null)).isNull();
            assertThat(resolver.resolveViewName("redirect:/x", null)).isNull();
            assertThat(resolver.resolveViewName("../etc/passwd", null)).isNull();
        });
    }

    @Test
    void viewRendersWithContentTypeAndRequestData() {
        web.run(ctx -> {
            View view = ctx.getBean(LoomaViewResolver.class).resolveViewName("hello", null);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setUserPrincipal(new Named("ada"));
            MockHttpServletResponse response = new MockHttpServletResponse();
            view.render(Map.of("name", "<World>"), request, response);
            assertThat(response.getContentAsString()).isEqualTo("Hello &lt;World&gt;! ada");
            assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        });
    }

    @Test
    void controllerModelOverridesRequestData() {
        web.run(ctx -> {
            View view = ctx.getBean(LoomaViewResolver.class).resolveViewName("hello", null);
            MockHttpServletResponse response = new MockHttpServletResponse();
            view.render(Map.of("name", "x", "principal", "model-wins"), new MockHttpServletRequest(), response);
            assertThat(response.getContentAsString()).contains("model-wins");
        });
    }

    @Test
    void templateErrorsDoNotLeakPartialOutput() {
        web.withBean("brokenEngine", LoomaEngine.class, () -> new LoomaEngine(new TemplateLoader() {
            public boolean exists(String n) { return true; }
            public String load(String n) { return "partial {{ $x.nope.deeper }}"; }
        }, false)).run(ctx -> {
            View view = ctx.getBean(LoomaViewResolver.class).resolveViewName("any", null);
            MockHttpServletResponse response = new MockHttpServletResponse();
            try {
                view.render(Map.of("x", "s"), new MockHttpServletRequest(), response);
            } catch (Exception expected) {
                // rendering failed as expected
            }
            assertThat(response.getContentAsString()).isEmpty();
        });
    }

    @Test
    void directiveBeansAreRegisteredAutomatically() {
        web.withBean("shout", io.github.djamelkorei.looma.spi.Directive.class, () -> new io.github.djamelkorei.looma.spi.Directive() {
            public String name() { return "shout"; }
            public io.github.djamelkorei.looma.spi.Node parse(io.github.djamelkorei.looma.spi.ParseContext p) {
                io.github.djamelkorei.looma.spi.Expr value = p.expr();
                return ctx -> ctx.writeEscaped(String.valueOf(ctx.eval(value)).toUpperCase() + "!");
            }
        }).withPropertyValues("looma.prefix=custom/", "looma.suffix=.tpl").run(ctx ->
                assertThat(ctx.getBean(LoomaEngine.class).render("shouting", Map.of("x", "hey"))).isEqualTo("HEY!"));
    }

    @Test
    void startupFailsListingEveryBrokenTemplate() {
        web.withPropertyValues("looma.prefix=broken/", "looma.suffix=.tpl").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure().getMessage())
                    .contains("2 Looma template(s) have syntax errors")
                    .contains("In template 'oops' at line 2: Unclosed block: @if is never closed")
                    .contains("'oops2'")
                    .doesNotContain("'ok'")
                    .contains("looma.check-templates=false");
        });
    }

    @Test
    void startupCheckCanBeDisabled() {
        web.withPropertyValues("looma.prefix=broken/", "looma.suffix=.tpl", "looma.check-templates=false")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void validTemplatesPassTheStartupCheck() {
        web.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(LoomaTemplateChecker.class);
        });
    }

    @Test
    void startupFailsOnReferencesToMissingTemplates() {
        web.withPropertyValues("looma.prefix=missing/", "looma.suffix=.tpl").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure().getMessage())
                    .contains("In template 'page' at line 2: References missing template 'does.not.exist'");
        });
    }

    @Test
    void templateDirLoadsFromDiskAndReloadsWithoutCaching(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path file = dir.resolve("live.looma.html");
        java.nio.file.Files.writeString(file, "one");
        web.withPropertyValues("looma.template-dir=" + dir).run(ctx -> {
            LoomaEngine engine = ctx.getBean(LoomaEngine.class);
            assertThat(engine.render("live", Map.of())).isEqualTo("one");
            java.nio.file.Files.writeString(file, "two");
            assertThat(engine.render("live", Map.of())).as("cache is off by default with template-dir").isEqualTo("two");
        });
    }

    @Test
    void templateDirIsCheckedAtStartupAndCachingCanBeForcedOn(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        java.nio.file.Path file = dir.resolve("sub/x.looma.html");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file, "@if(true)never closed");
        web.withPropertyValues("looma.template-dir=" + dir).run(ctx ->
                assertThat(ctx.getStartupFailure()).hasMessageContaining("In template 'sub/x'"));
        java.nio.file.Files.writeString(file, "cached");
        web.withPropertyValues("looma.template-dir=" + dir, "looma.cache=true").run(ctx -> {
            LoomaEngine engine = ctx.getBean(LoomaEngine.class);
            assertThat(engine.render("sub/x", Map.of())).isEqualTo("cached");
            java.nio.file.Files.writeString(file, "changed");
            assertThat(engine.render("sub/x", Map.of())).isEqualTo("cached");
        });
    }
}
