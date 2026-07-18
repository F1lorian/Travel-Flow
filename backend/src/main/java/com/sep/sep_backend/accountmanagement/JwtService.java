package com.sep.sep_backend.accountmanagement;

import com.sep.sep_backend.entities.User;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the bearer tokens used for all authenticated requests.
 *
 * <p>This is a hand-rolled JWT implementation, not a library one. Tokens are standard HS256 —
 * base64url header, payload and an HMAC-SHA256 signature computed with {@link javax.crypto.Mac} —
 * but claims are read back out of the payload with regexes ({@link #extractString}, {@link
 * #extractLong}) rather than a JSON parser. That is only safe because this service is also the sole
 * producer of these tokens and the signature is checked before any claim is read; a token from
 * anywhere else should not be fed through here.
 *
 * <p>Tokens live 24 hours and carry {@code sub}, {@code username}, {@code email}, {@code iat} and
 * {@code exp}. There are no roles or authorities anywhere in the app, so none are encoded. The
 * signing key comes from {@code jwt.secret}, which has a committed development default that must be
 * overridden in any real deployment.
 */
@Service
public class JwtService {
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final long TOKEN_SECONDS = 24 * 60 * 60;

  private final String secret;

  public JwtService(@Value("${jwt.secret:sep-development-secret-change-me}") String secret) {
    this.secret = secret;
  }

  /**
   * Signs a 24-hour token for the given user.
   *
   * @param user the authenticated user; its name and email are embedded as claims
   * @return the compact {@code header.payload.signature} token
   */
  public String createToken(User user) {
    long issuedAt = Instant.now().getEpochSecond();
    long expiresAt = issuedAt + TOKEN_SECONDS;

    String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    String payload =
        "{"
            + "\"sub\":\""
            + escape(user.getName())
            + "\","
            + "\"username\":\""
            + escape(user.getName())
            + "\","
            + "\"email\":\""
            + escape(user.getEmail())
            + "\","
            + "\"iat\":"
            + issuedAt
            + ","
            + "\"exp\":"
            + expiresAt
            + "}";

    String encodedHeader = base64Url(header.getBytes(StandardCharsets.UTF_8));
    String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
    String unsignedToken = encodedHeader + "." + encodedPayload;
    return unsignedToken + "." + sign(unsignedToken);
  }

  /**
   * Verifies a token's signature and expiry and returns the caller's identity.
   *
   * <p>Note this returns the {@code username} claim, not {@code sub}. The two hold the same value
   * today, but downstream lookups accept a username <em>or</em> an email, so callers should treat
   * the result as "whatever identifies the user" rather than assume either form.
   *
   * @param token the bare token, without the {@code Bearer } prefix
   * @return the username claim
   * @throws IllegalArgumentException if the token is not three dot-separated parts, if the
   *     signature does not match, if it has expired, or if the username claim is absent
   */
  public String validateToken(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid token format");
    }

    String unsignedToken = parts[0] + "." + parts[1];
    if (!sign(unsignedToken).equals(parts[2])) {
      throw new IllegalArgumentException("Invalid token signature");
    }

    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

    long exp = extractLong(payload, "exp");
    if (Instant.now().getEpochSecond() > exp) {
      throw new IllegalArgumentException("Token has expired");
    }

    return extractString(payload, "username");
  }

  private String sign(String unsignedToken) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return base64Url(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not create JWT", exception);
    }
  }

  private String base64Url(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  private String extractString(String json, String key) {
    Matcher matcher = Pattern.compile("\"" + key + "\":\"([^\"]+)\"").matcher(json);
    if (matcher.find()) return matcher.group(1);
    throw new IllegalArgumentException("Missing claim: " + key);
  }

  private long extractLong(String json, String key) {
    Matcher matcher = Pattern.compile("\"" + key + "\":(\\d+)").matcher(json);
    if (matcher.find()) return Long.parseLong(matcher.group(1));
    throw new IllegalArgumentException("Missing claim: " + key);
  }

  /**
   * Escapes backslashes and quotes so a name or email cannot break out of the hand-built JSON
   * payload. Control characters are not escaped; the fields fed in here are validated at
   * registration and never contain them.
   */
  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
