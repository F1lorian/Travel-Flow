package com.sep.sep_backend.gmailimport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts Gmail OAuth tokens before they are written to the database so that a
 * database dump alone never exposes usable Gmail credentials.
 *
 * <p>A fixed salt is derived once from the configured secret; the same secret must be used for the
 * lifetime of a stored token, otherwise previously encrypted tokens can no longer be read.
 */
@Component
public class TokenCipher {

  private final TextEncryptor encryptor;

  public TokenCipher(@Value("${gmail.token.encryption-secret}") String secret) {
    // A stable, hex-encoded salt derived deterministically from the secret keeps encryption
    // reversible across restarts without committing a separate salt value.
    String salt = deriveSalt(secret);
    this.encryptor = Encryptors.text(secret, salt);
  }

  /**
   * Encrypts a plaintext token.
   *
   * @param plaintext token value, may be {@code null}
   * @return encrypted value, or {@code null} if the input was {@code null}
   */
  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    return encryptor.encrypt(plaintext);
  }

  /**
   * Decrypts a previously encrypted token.
   *
   * @param ciphertext encrypted value, may be {@code null}
   * @return plaintext token, or {@code null} if the input was {@code null}
   */
  public String decrypt(String ciphertext) {
    if (ciphertext == null) {
      return null;
    }
    return encryptor.decrypt(ciphertext);
  }

  private static String deriveSalt(String secret) {
    // Spring's Encryptors expect the salt as a hex string. We hash the secret to 8 bytes of hex so
    // the salt is deterministic yet not a trivially guessable constant.
    int hash = secret == null ? 0 : secret.hashCode();
    long expanded = ((long) hash << 32) ^ (hash * 0x9E3779B97F4A7C15L);
    return String.format("%016x", expanded);
  }
}
