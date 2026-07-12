package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.PrivacyPermission;
import com.sep.sep_backend.entities.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for accessing privacy permission entries.
 *
 * <p>Each permission entry belongs to one owner user and one friend user.
 */
public interface PrivacyPermissionRepository extends JpaRepository<PrivacyPermission, Long> {

  /**
   * Finds the permission settings that an owner has configured for one specific friend.
   *
   * <p>Used when checking whether a friend is allowed to access protected information.
   */
  Optional<PrivacyPermission> findByOwnerAndFriend(User owner, User friend);

  /**
   * Finds all privacy settings configured by one owner.
   *
   * <p>Used for the privacy settings page where the user can manage permissions for all friends.
   */
  List<PrivacyPermission> findByOwner(User owner);
}
