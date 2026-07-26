package com.dotblog.notification.service;

import com.dotblog.notification.config.AppProperties;
import com.dotblog.notification.config.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails via Resend.
 * If API key / sender email is missing, logs a warning and skips the send (dev-friendly stub).
 */
@Service
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailProperties emailProperties;
    private final AppProperties appProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public EmailService(EmailProperties emailProperties, AppProperties appProperties) {
        this.emailProperties = emailProperties;
        this.appProperties = appProperties;
    }

    /** Send OTP for registration / resend flow (plain text). */
    public boolean sendOtpText(String toEmail, String otp) {
        String text = "Hi,\n Your OTP is: " + otp;
        return send(toEmail, "OTP Verification for Dot-Blog", text, null);
    }

    /** Send forgot-password reset link (HTML). The `token` is the ForgotPassword doc id. */
    public boolean sendForgotPasswordLink(String toEmail, String token) {
        String link = appProperties.getFrontendUrl() + "/reset-password/" + token;
        String html = "<p>Hi, please click the link below to change your password:</p>"
                + "<a href=\"" + link + "\">Reset Password</a>";
        return send(toEmail, "Change account Password request", null, html);
    }

    private boolean send(String toEmail, String subject, String textContent, String htmlContent) {
        if (!emailProperties.isConfigured()) {
            log.warn("Resend not configured (RESEND_API_KEY / SENDER_MAIL_ID); skipping send to {}", toEmail);
            return true;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(emailProperties.getApiKey());

        String from = "Dot-Blog <" + emailProperties.getSenderEmail() + ">";
        Map<String, Object> body = new HashMap<>();
        body.put("from", from);
        body.put("to", List.of(toEmail));
        body.put("subject", subject);
        if (textContent != null && !textContent.isBlank()) body.put("text", textContent);
        if (htmlContent != null && !htmlContent.isBlank()) body.put("html", htmlContent);

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
