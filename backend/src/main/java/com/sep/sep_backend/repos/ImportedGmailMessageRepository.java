package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.ImportedGmailMessage;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the record of Gmail messages already imported as calendar events. */
public interface ImportedGmailMessageRepository extends JpaRepository<ImportedGmailMessage, Long> {

  boolean existsByUserIdAndMessageId(long userId, String messageId);
}
