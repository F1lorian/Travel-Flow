package com.sep.sep_backend.gmailimport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client for the Gmail REST API. It is deliberately stateless: every call takes the
 * access token as an argument, which keeps it free of any dependency on the token-storage/refresh
 * logic and makes it easy to mock.
 *
 * <p>All Gmail HTTP failures are translated into the feature's own exceptions so callers never have
 * to reason about raw HTTP status codes.
 */
@Component
public class GmailApiClient {

  private static final String DEFAULT_BASE_URL = "https://gmail.googleapis.com/gmail/v1";

  private final RestClient restClient;

  public GmailApiClient(
      RestClient.Builder restClientBuilder,
      @Value("${gmail.api.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
    this.restClient = restClientBuilder.baseUrl(baseUrl).build();
  }

  /**
   * Returns the Gmail address of the authenticated mailbox.
   *
   * @param accessToken valid OAuth access token
   * @return the mailbox email address
   */
  public String getProfileEmailAddress(String accessToken) {
    JsonNode node = get("/users/me/profile", accessToken);
    return node.path("emailAddress").asText(null);
  }

  /**
   * Lists the ids of the most recent inbox messages.
   *
   * @param accessToken valid OAuth access token
   * @param maxResults maximum number of message ids to return
   * @return message ids ordered from newest to oldest (possibly empty, never {@code null})
   */
  public List<String> listRecentMessageIds(String accessToken, int maxResults) {
    JsonNode node =
        get("/users/me/messages?maxResults=" + maxResults + "&labelIds=INBOX", accessToken);
    List<String> ids = new ArrayList<>();
    JsonNode messages = node.path("messages");
    if (messages.isArray()) {
      for (JsonNode message : messages) {
        String id = message.path("id").asText(null);
        if (id != null) {
          ids.add(id);
        }
      }
    }
    return ids;
  }

  /**
   * Fetches a single message in raw RFC 822 form so it can be parsed like an uploaded {@code .eml}
   * file.
   *
   * @param accessToken valid OAuth access token
   * @param messageId Gmail message id
   * @return the decoded raw message bytes
   */
  public byte[] getRawMessage(String accessToken, String messageId) {
    JsonNode node = get("/users/me/messages/" + messageId + "?format=raw", accessToken);
    String raw = node.path("raw").asText(null);
    if (raw == null) {
      throw new GmailApiException("Gmail message " + messageId + " did not contain raw content");
    }
    return Base64.getUrlDecoder().decode(raw);
  }

  private JsonNode get(String path, String accessToken) {
    try {
      JsonNode node =
          restClient
              .get()
              .uri(path)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .body(JsonNode.class);
      if (node == null) {
        throw new GmailApiException("Gmail API returned an empty response for " + path);
      }
      return node;
    } catch (RestClientResponseException exception) {
      throw translate(exception, path);
    }
  }

  private RuntimeException translate(RestClientResponseException exception, String path) {
    HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
    if (status == HttpStatus.TOO_MANY_REQUESTS) {
      return new GmailRateLimitException("Gmail API rate limit reached, please try again later");
    }
    if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
      return new GmailAuthException(
          "Gmail access was rejected (" + exception.getStatusText() + "), please reconnect Gmail");
    }
    return new GmailApiException(
        "Gmail API call to " + path + " failed: " + exception.getStatusText(), exception);
  }
}
