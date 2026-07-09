package com.sep.sep_backend.gmailimport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Symmetric encryption for Gmail OAuth refresh tokens stored in the database. Uses AES-256 GCM
 * (Spring Security {@link Encryptors#delux}) keyed by {@code gmail.token.encryption-secret}; each
 * call uses a fresh random IV, so identical plaintexts produce different ciphertexts.
 */
@Component
public class TokenCipher {

  // Fixed key-derivation salt (hex). Uniqueness of ciphertexts comes from the per-message GCM IV,
  // not from this salt, so a constant is safe here.
  private static final String SALT = "7369676e616c73616c743132333435ab";

  // Fallback so the bean stays constructible in dev/test when no secret is configured; production
  // must always override gmail.token.encryption-secret with a real value.
  private static final String DEV_FALLBACK_SECRET = "sep-development-gmail-token-secret-change-me";

  private final TextEncryptor encryptor;

  public TokenCipher(@Value("${gmail.token.encryption-secret:}") String secret) {
    String key = StringUtils.hasText(secret) ? secret : DEV_FALLBACK_SECRET;
    this.encryptor = Encryptors.delux(key, SALT);
  }

  public String encrypt(String plaintext) {
    return encryptor.encrypt(plaintext);
  }

  public String decrypt(String ciphertext) {
    return encryptor.decrypt(ciphertext);
  }
}
