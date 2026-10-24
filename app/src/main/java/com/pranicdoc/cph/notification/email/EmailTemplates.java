package com.pranicdoc.cph.notification.email;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the HTML emails in {@code resources/templates/email/}. Values are HTML-escaped by
 * Thymeleaf ({@code th:text}), so names people type in can't inject markup.
 */
@Component
public class EmailTemplates {

    private final TemplateEngine engine = new TemplateEngine();

    public EmailTemplates() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        engine.setTemplateResolver(resolver);
    }

    public String render(String template, Map<String, Object> variables) {
        return engine.process(template, new Context(Locale.ENGLISH, variables));
    }
}
