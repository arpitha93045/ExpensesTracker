package com.expensetracker.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.notifications.enabled:false}")
    private boolean enabled;

    @Value("${app.notifications.from-email:noreply@expensetracker.com}")
    private String fromEmail;

    public void sendHtml(String to, String subject, String htmlBody) {
        if (!enabled) {
            log.debug("Notifications disabled — skipping email to {} | {}", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to {} | {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {} | {}: {}", to, subject, e.getMessage());
        }
    }
}
