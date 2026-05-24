package com.dotblog.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.encryption")
public class EncryptionProperties {

    /** Two-way secret for encrypting/decrypting userId (e.g. resend OTP). Match Node TWO_WAY_SECRET. */
    private String twoWaySecret = "";

    public String getTwoWaySecret() { return twoWaySecret; }
    public void setTwoWaySecret(String twoWaySecret) { this.twoWaySecret = twoWaySecret; }
}
