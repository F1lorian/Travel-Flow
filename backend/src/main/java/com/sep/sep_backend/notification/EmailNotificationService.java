package com.sep.sep_backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Single place through which the application sends emails to users: one-time codes, "you were added
 * to a trip" notices, security notices after a password change, and so on.
 *
 * <p>Mail delivery must never break the action that triggered it (a trip invite still succeeds when
 * the SMTP server is down), so callers normally use {@link #sendBestEffort}. Callers whose reply to
 * the user depends on delivery (e.g. "code sent" vs. "could not be sent") use {@link #trySend} and
 * check the result.
 */
@Service
public class EmailNotificationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);

  private final JavaMailSender mailSender;
  private final String mailUsername;

  public EmailNotificationService(
      JavaMailSender mailSender, @Value("${spring.mail.username:}") String mailUsername) {
    this.mailSender = mailSender;
    this.mailUsername = mailUsername;
  }

  /**
   * Sends a plain-text email and reports whether delivery was handed to the mail server. Failures
   * are logged, never thrown.
   */
  public boolean trySend(String to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailUsername);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    try {
      mailSender.send(message);
      return true;
    } catch (MailException exception) {
      LOGGER.warn("Email \"{}\" could not be sent to {}", subject, to, exception);
      return false;
    }
  }

  /** Fire-and-forget variant of {@link #trySend} for notifications that are nice-to-have. */
  public void sendBestEffort(String to, String subject, String body) {
    trySend(to, subject, body);
  }
}
