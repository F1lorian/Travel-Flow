package com.sep.sep_backend.gmailimport;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.sep.sep_backend.gmailimport.dto.GmailMessagePreviewDTO;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over the Gmail REST API (read-only). Each call takes a short-lived access token and
 * builds a request-scoped {@link Gmail} client that attaches it as a bearer token.
 */
@Component
public class GmailClient {

  private static final String USER = "me";
  private static final String APPLICATION_NAME = "sep-backend";

  private final NetHttpTransport transport = new NetHttpTransport();
  private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

  /** Returns the address of the connected mailbox (used to show which account is linked). */
  public String getProfileEmail(String accessToken) {
    try {
      return gmail(accessToken).users().getProfile(USER).execute().getEmailAddress();
    } catch (Exception exception) {
      throw new IllegalStateException("Could not read Gmail profile", exception);
    }
  }

  /**
   * Lists the most recent messages with lightweight metadata for the picker.
   *
   * @param accessToken live access token
   * @param max maximum number of messages to return
   * @return previews ordered as Gmail returns them (most recent first)
   */
  public List<GmailMessagePreviewDTO> listRecent(String accessToken, int max) {
    try {
      Gmail gmail = gmail(accessToken);
      ListMessagesResponse list =
          gmail.users().messages().list(USER).setMaxResults((long) max).execute();

      List<Message> messages = list.getMessages();
      if (messages == null || messages.isEmpty()) {
        return List.of();
      }

      return messages.stream().map(message -> toPreview(gmail, message.getId())).toList();
    } catch (Exception exception) {
      throw new IllegalStateException("Could not list Gmail messages", exception);
    }
  }

  /**
   * Fetches the raw RFC822 bytes of a message so they can be parsed by the shared mail parser.
   *
   * @param accessToken live access token
   * @param messageId Gmail message id
   * @return decoded RFC822 message bytes
   */
  public byte[] getRaw(String accessToken, String messageId) {
    try {
      Message message =
          gmail(accessToken).users().messages().get(USER, messageId).setFormat("raw").execute();
      if (message == null || message.getRaw() == null) {
        throw new EntityNotFoundException("Gmail message " + messageId + " not found");
      }
      return message.decodeRaw();
    } catch (EntityNotFoundException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Could not read Gmail message " + messageId, exception);
    }
  }

  private GmailMessagePreviewDTO toPreview(Gmail gmail, String messageId) {
    try {
      Message message =
          gmail
              .users()
              .messages()
              .get(USER, messageId)
              .setFormat("metadata")
              .setMetadataHeaders(List.of("Subject", "From", "Date"))
              .execute();
      return new GmailMessagePreviewDTO(
          messageId,
          header(message, "Subject"),
          header(message, "From"),
          header(message, "Date"),
          message.getSnippet() == null ? "" : message.getSnippet());
    } catch (Exception exception) {
      throw new IllegalStateException("Could not read Gmail message preview", exception);
    }
  }

  private String header(Message message, String name) {
    if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
      return "";
    }
    for (MessagePartHeader header : message.getPayload().getHeaders()) {
      if (name.equalsIgnoreCase(header.getName())) {
        return header.getValue() == null ? "" : header.getValue();
      }
    }
    return "";
  }

  private Gmail gmail(String accessToken) {
    HttpRequestInitializer initializer =
        request -> request.getHeaders().setAuthorization("Bearer " + accessToken);
    return new Gmail.Builder(transport, jsonFactory, initializer)
        .setApplicationName(APPLICATION_NAME)
        .build();
  }
}
