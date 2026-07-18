package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.GmailAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the Gmail account a user has connected for event import.
 *
 * <p>A user has at most one connected account, so the {@code userId} lookups below return or affect
 * a single row rather than a collection.
 */
public interface GmailAccountRepository extends JpaRepository<GmailAccount, Long> {
  /** Finds the account connected by the given user, if any. */
  Optional<GmailAccount> findByUserId(long userId);

  /** Returns whether the user currently has a Gmail account connected. */
  boolean existsByUserId(long userId);

  /**
   * Removes the user's connected account, along with the encrypted refresh token.
   *
   * <p>Used both when disconnecting and as part of account deletion. Deletion cascades in this app
   * are hand-written rather than declared on the entities, so this has to be called explicitly.
   * Derived delete queries need an active transaction at the call site.
   */
  void deleteByUserId(long userId);
}
