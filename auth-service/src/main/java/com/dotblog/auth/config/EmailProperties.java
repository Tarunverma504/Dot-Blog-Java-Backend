package com.dotblog.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private String apiKey = "";
    private String senderEmail = "";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey != null ? apiKey : ""; }
    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail != null ? senderEmail : ""; }
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && senderEmail != null && !senderEmail.isBlank();
    }
}
