package com.sep.sep_backend.repos;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.entities.FriendRequest;
import java.util.List;
import java.util.Optional;
import com.sep.sep_backend.friends.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    Optional<FriendRequest> findBySenderAndReceiverAndStatus(
            User sender, User receiver, FriendRequestStatus status);

    Optional<FriendRequest> findByIdAndReceiver(long id, User receiver);

    List<FriendRequest> findByReceiverAndStatus(User receiver, FriendRequestStatus status);

    List<FriendRequest> findBySenderAndStatus(User sender, FriendRequestStatus status);
}