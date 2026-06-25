package com.sep.sep_backend.accountmanagement;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Verifies Google ID tokens (the credential the frontend receives from Google Identity Services)
 * against Google's public keys and the configured OAuth client id.
 */
@Service
public class GoogleTokenVerifier {

  /** Minimal view of the verified Google account used to find or create a local user. */
  public record GoogleUserInfo(
      String email, boolean emailVerified, String firstName, String surname) {}

  private final String clientId;
  private final GoogleIdTokenVerifier verifier;

  public GoogleTokenVerifier(@Value("${google.oauth.client-id:}") String clientId) {
    this.clientId = clientId;
    this.verifier =
        new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(clientId))
            .build();
  }

  public GoogleUserInfo verify(String idTokenString) {
    if (!StringUtils.hasText(clientId)) {
      throw new IllegalStateException("Google login is not configured");
    }
    if (!StringUtils.hasText(idTokenString)) {
      throw new IllegalArgumentException("Google credential is required");
    }

    GoogleIdToken idToken;
    try {
      idToken = verifier.verify(idTokenString);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Could not verify Google credential");
    }
    if (idToken == null) {
      throw new IllegalArgumentException("Invalid Google credential");
    }

    Payload payload = idToken.getPayload();
    Boolean emailVerified = payload.getEmailVerified();
    return new GoogleUserInfo(
        payload.getEmail(),
        Boolean.TRUE.equals(emailVerified),
        asString(payload.get("given_name")),
        asString(payload.get("family_name")));
  }

  private String asString(Object value) {
    return value == null ? null : value.toString();
  }
}
