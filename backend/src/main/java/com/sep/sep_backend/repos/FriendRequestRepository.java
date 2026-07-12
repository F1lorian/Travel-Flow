package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.FriendRequest;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.friends.FriendRequestStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link FriendRequest} entities. Queries are always scoped by sender,
 * receiver, and/or status so the friends feature can look up pending requests in either direction
 * without loading unrelated rows.
 */
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

  /** Finds the request a specific sender directed at a specific receiver in the given status. */
  Optional<FriendRequest> findBySenderAndReceiverAndStatus(
      User sender, User receiver, FriendRequestStatus status);

  /** Finds a request by id, but only if it is addressed to the given receiver. */
  Optional<FriendRequest> findByIdAndReceiver(long id, User receiver);

  /** Lists all requests addressed to the given receiver in the given status. */
  List<FriendRequest> findByReceiverAndStatus(User receiver, FriendRequestStatus status);

  /** Lists all requests created by the given sender in the given status. */
  List<FriendRequest> findBySenderAndStatus(User sender, FriendRequestStatus status);

  /** Lists every request the given user participates in, on either side. */
  List<FriendRequest> findBySenderOrReceiver(User sender, User receiver);
}
