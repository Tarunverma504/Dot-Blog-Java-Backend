package com.dotblog.auth.service;

import com.dotblog.auth.config.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails via <a href="https://resend.com/docs/api-reference/emails/send-email">Resend</a>.
 * If API key is missing, logs and skips sending (stub behavior).
 */
@Service
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public EmailService(EmailProperties properties) {
        this.properties = properties;
    }

    /** Send OTP as plain text. */
    public void sendOtp(String toEmail, String textContent) {
        if (!properties.isConfigured()) {
            log.warn("Resend not configured (RESEND_API_KEY / SENDER_MAIL_ID); skipping send OTP to {}", toEmail);
            return;
        }
        send(toEmail, "OTP Verification for Dot-Blog", textContent, null);
    }

    /** Send HTML email (e.g. reset password link). */
    public boolean sendResetLink(String toEmail, String htmlContent, String subject) {
        if (!properties.isConfigured()) {
            log.warn("Resend not configured; skipping send reset link to {}", toEmail);
            return true;
        }
        return send(toEmail, subject != null ? subject : "Change account Password request", null, htmlContent);
    }

    private boolean send(String toEmail, String subject, String textContent, String htmlContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        String from = "Dot-Blog <" + properties.getSenderEmail() + ">";
        Map<String, Object> body = new HashMap<>();
        body.put("from", from);
        body.put("to", List.of(toEmail));
        body.put("subject", subject != null ? subject : "");
        if (textContent != null && !textContent.isBlank()) {
            body.put("text", textContent);
        }
        if (htmlContent != null && !htmlContent.isBlank()) {
            body.put("html", htmlContent);
        }

        try {
            ResponseEntity<String> res = restTemplate.exchange(
                    RESEND_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
