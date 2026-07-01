package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.GmailAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the per-user Gmail OAuth token storage. */
public interface GmailAccountRepository extends JpaRepository<GmailAccount, Long> {

  Optional<GmailAccount> findByUserId(long userId);

  boolean existsByUserId(long userId);

  void deleteByUserId(long userId);
}
