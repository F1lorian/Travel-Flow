package com.sep.sep_backend.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

  @Mock private JavaMailSender mailSender;

  @Test
  void trySend_sendsMailWithGivenFields_andReportsSuccess() {
    EmailNotificationService service = new EmailNotificationService(mailSender, "noreply@sep.de");

    boolean sent = service.trySend("max@example.com", "Hello", "Body text");

    assertTrue(sent);
    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    SimpleMailMessage message = captor.getValue();
    assertEquals("noreply@sep.de", message.getFrom());
    assertArrayEquals(new String[] {"max@example.com"}, message.getTo());
    assertEquals("Hello", message.getSubject());
    assertEquals("Body text", message.getText());
  }

  @Test
  void trySend_reportsFailureInsteadOfThrowing_whenMailServerIsDown() {
    EmailNotificationService service = new EmailNotificationService(mailSender, "noreply@sep.de");
    doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

    assertFalse(service.trySend("max@example.com", "Hello", "Body text"));
  }

  @Test
  void sendBestEffort_neverThrows_whenMailServerIsDown() {
    EmailNotificationService service = new EmailNotificationService(mailSender, "noreply@sep.de");
    doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

    assertDoesNotThrow(() -> service.sendBestEffort("max@example.com", "Hello", "Body text"));
  }
}
