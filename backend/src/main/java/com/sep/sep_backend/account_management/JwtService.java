package com.sep.sep_backend.account_management;

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

@Service
public class JwtService {
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final long TOKEN_SECONDS = 24 * 60 * 60;

  private final String secret;

  public JwtService(@Value("${jwt.secret:sep-development-secret-change-me}") String secret) {
    this.secret = secret;
  }

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

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
