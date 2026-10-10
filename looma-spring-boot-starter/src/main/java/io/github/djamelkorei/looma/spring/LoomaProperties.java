package io.github.djamelkorei.looma.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("looma")
public class LoomaProperties {

    /** Classpath folder holding templates. */
    private String prefix = "templates/";

    /** Suffix appended to the view name. */
    private String suffix = ".looma.html";

    /**
     * Cache parsed templates. Unset means: on, except when looma.template-dir is set (development), where
     * it is off so edits show up on the next request.
     */
    private Boolean cache;

    /**
     * Load templates from this folder on disk instead of the classpath, e.g. src/main/resources/templates,
     * for live reload without rebuilding.
     */
    private String templateDir;

    /** Content-Type of rendered responses. */
    private String contentType = "text/html;charset=UTF-8";

    /** Parse all templates at startup and fail fast on syntax errors. */
    private boolean checkTemplates = true;

    public boolean isCheckTemplates() { return checkTemplates; }
    public void setCheckTemplates(boolean checkTemplates) { this.checkTemplates = checkTemplates; }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }
    public boolean isCache() { return cache != null ? cache : templateDir == null; }
    public void setCache(Boolean cache) { this.cache = cache; }
    public String getTemplateDir() { return templateDir; }
    public void setTemplateDir(String templateDir) { this.templateDir = templateDir; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
