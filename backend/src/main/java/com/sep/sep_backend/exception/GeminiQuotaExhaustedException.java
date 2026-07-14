package com.sep.sep_backend.exception;

/**
 * Gemini refused the call because the quota is used up (HTTP 429).
 *
 * <p>Distinct from {@link EventDetectionException}, which means "this one mail could not be
 * checked". A quota refusal is not per-mail: it kills the whole import, and no other mail in the
 * batch will fare better. Retrying inside the request is pointless — the free tier's binding limit
 * is measured <em>per day</em> (20 requests for {@code gemini-2.5-flash}), so a backoff of seconds
 * cannot wait it out.
 *
 * <p>It therefore travels all the way out to the controller and becomes an HTTP 429 with a readable
 * message, instead of being counted as N mails that mysteriously "could not be checked".
 */
public class GeminiQuotaExhaustedException extends RuntimeException {

  public GeminiQuotaExhaustedException(String message, Throwable cause) {
    super(message, cause);
  }
}
