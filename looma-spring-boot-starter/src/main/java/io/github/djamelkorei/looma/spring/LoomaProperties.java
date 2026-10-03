package io.github.djamelkorei.looma.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("looma")
public class LoomaProperties {

    /** Classpath folder holding templates. */
    private String prefix = "templates/";

    /** Suffix appended to the view name. */
    private String suffix = ".looma.html";

    /** Cache parsed templates. Disable during development. */
    private boolean cache = true;

    /** Content-Type of rendered responses. */
    private String contentType = "text/html;charset=UTF-8";

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }
    public boolean isCache() { return cache; }
    public void setCache(boolean cache) { this.cache = cache; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
