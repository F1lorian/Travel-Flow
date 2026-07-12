package com.sep.sep_backend.friends;

import com.sep.sep_backend.entities.FriendRequest;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.FriendRequestRepository;
import com.sep.sep_backend.repos.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages the friendship lifecycle: searching users, sending/accepting/declining friend requests,
 * and removing friends.
 *
 * <p>Every state change fans out to two channels: an in-app WebSocket notification via {@link
 * FriendRequestNotificationService} and a best-effort email via {@link EmailNotificationService},
 * so users learn about requests even while logged out. Friendships are stored symmetrically — both
 * users appear in each other's friends list.
 */
@Service
public class FriendsService {
  private final UserRepository userRepository;
  private final FriendRequestRepository friendRequestRepository;
  private final FriendRequestNotificationService notificationService;
  private final EmailNotificationService emailNotificationService;

  public FriendsService(
      UserRepository userRepository,
      FriendRequestRepository friendRequestRepository,
      FriendRequestNotificationService notificationService,
      EmailNotificationService emailNotificationService) {
    this.userRepository = userRepository;
    this.friendRequestRepository = friendRequestRepository;
    this.notificationService = notificationService;
    this.emailNotificationService = emailNotificationService;
  }

  /**
   * Returns all confirmed friends of the given user.
   *
   * @param currentUsername username or email of the authenticated user
   * @return the user's friends, each marked as connected
   */
  public List<FriendDTO> getFriends(String currentUsername) {
    User currentUser = getCurrentUserOrThrow(currentUsername);

    return currentUser.getFriends().stream().map(friend -> toFriendDTO(friend, true)).toList();
  }

  /**
   * Searches all users whose username, first name, or surname contains the given text
   * (case-insensitive). The searching user is excluded from the results.
   *
   * @param currentUsername username or email of the authenticated user
   * @param name search text matched against username, first name, and surname
   * @return matching users, each flagged with whether they are already a friend
   */
  public List<FriendDTO> searchUsers(String currentUsername, String name) {
    User currentUser = getCurrentUserOrThrow(currentUsername);

    return userRepository
        .findByNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrSurnameContainingIgnoreCase(
            name, name, name)
        .stream()
        .filter(user -> user.getId() != currentUser.getId())
        .map(user -> toFriendDTO(user, isConnected(currentUser, user)))
        .toList();
  }

  /**
   * Sends a friend request to another user, notifying the receiver in-app and by email.
   *
   * <p>If the receiver already has a pending request towards the sender, the two requests are
   * treated as mutual interest and the existing request is accepted immediately instead of
   * creating a duplicate.
   *
   * @param currentUsername username or email of the sending user
   * @param receiverId id of the user to befriend
   * @return the created (or auto-accepted) request
   * @throws ResponseStatusException 400 when sending to oneself, 409 when already friends or a
   *     request is already pending
   */
  @Transactional
  public FriendRequestDTO sendFriendRequest(String currentUsername, long receiverId) {
    User sender = getCurrentUserOrThrow(currentUsername);
    User receiver = getUserOrThrow(receiverId);

    if (sender.getId() == receiver.getId()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Users cannot send a friend request to themselves");
    }

    if (isConnected(sender, receiver)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Users are already friends");
    }

    friendRequestRepository
        .findBySenderAndReceiverAndStatus(sender, receiver, FriendRequestStatus.PENDING)
        .ifPresent(
            r -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend request already sent");
            });

    // Falls der Empfänger dem Sender bereits eine offene Anfrage geschickt hat,
    // nehmen wir diese direkt an, statt eine zweite Anfrage anzulegen.
    var reverseRequest =
        friendRequestRepository.findBySenderAndReceiverAndStatus(
            receiver, sender, FriendRequestStatus.PENDING);

    if (reverseRequest.isPresent()) {
      FriendRequestDTO dto = acceptInternal(reverseRequest.get());
      notificationService.notifyRequestAnswered(dto);
      sendFriendsNowEmails(sender, receiver);
      return dto;
    }

    FriendRequest request = new FriendRequest(sender, receiver);
    friendRequestRepository.save(request);

    FriendRequestDTO dto = toFriendRequestDTO(request);
    notificationService.notifyNewRequest(dto);

    // The receiver learns about the request even while logged out.
    emailNotificationService.sendBestEffort(
        receiver.getEmail(),
        "New friend request on SEP",
        sender.getName() + " sent you a friend request on SEP.");
    return dto;
  }

  /**
   * Accepts a pending friend request addressed to the current user, linking both users as friends
   * and emailing both sides about the new friendship.
   *
   * @param currentUsername username or email of the receiving user
   * @param requestId id of the request to accept
   * @return the accepted request
   * @throws ResponseStatusException 404 when no such request is addressed to this user, 409 when
   *     the request was already answered
   */
  @Transactional
  public FriendRequestDTO acceptFriendRequest(String currentUsername, long requestId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);

    FriendRequest request =
        friendRequestRepository
            .findByIdAndReceiver(requestId, currentUser)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found"));

    if (request.getStatus() != FriendRequestStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Friend request has already been answered");
    }

    FriendRequestDTO dto = acceptInternal(request);
    notificationService.notifyRequestAnswered(dto);
    sendFriendsNowEmails(request.getSender(), request.getReceiver());
    return dto;
  }

  /**
   * Declines a pending friend request addressed to the current user and informs the sender by
   * email.
   *
   * @param currentUsername username or email of the receiving user
   * @param requestId id of the request to decline
   * @return the declined request
   * @throws ResponseStatusException 404 when no such request is addressed to this user, 409 when
   *     the request was already answered
   */
  @Transactional
  public FriendRequestDTO declineFriendRequest(String currentUsername, long requestId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);

    FriendRequest request =
        friendRequestRepository
            .findByIdAndReceiver(requestId, currentUser)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found"));

    if (request.getStatus() != FriendRequestStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Friend request has already been answered");
    }

    request.setStatus(FriendRequestStatus.DECLINED);
    friendRequestRepository.save(request);

    FriendRequestDTO dto = toFriendRequestDTO(request);
    notificationService.notifyRequestAnswered(dto);

    // The sender learns their request was turned down; the receiver made an explicit choice
    // so they don't need a confirmation email for their own action.
    emailNotificationService.sendBestEffort(
        request.getSender().getEmail(),
        "Friend request declined",
        request.getReceiver().getName() + " declined your friend request on SEP.");
    return dto;
  }

  /**
   * Returns all pending friend requests other users have sent to the current user.
   *
   * @param currentUsername username or email of the authenticated user
   * @return pending incoming requests
   */
  public List<FriendRequestDTO> getIncomingRequests(String currentUsername) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    return friendRequestRepository
        .findByReceiverAndStatus(currentUser, FriendRequestStatus.PENDING)
        .stream()
        .map(this::toFriendRequestDTO)
        .toList();
  }

  /**
   * Returns all pending friend requests the current user has sent and that are still unanswered.
   *
   * @param currentUsername username or email of the authenticated user
   * @return pending outgoing requests
   */
  public List<FriendRequestDTO> getOutgoingRequests(String currentUsername) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    return friendRequestRepository
        .findBySenderAndStatus(currentUser, FriendRequestStatus.PENDING)
        .stream()
        .map(this::toFriendRequestDTO)
        .toList();
  }

  /** Links sender and receiver as friends on both sides and marks the request accepted. */
  private FriendRequestDTO acceptInternal(FriendRequest request) {
    User sender = request.getSender();
    User receiver = request.getReceiver();

    sender.getFriends().add(receiver);
    receiver.getFriends().add(sender);
    userRepository.save(sender);
    userRepository.save(receiver);

    request.setStatus(FriendRequestStatus.ACCEPTED);
    friendRequestRepository.save(request);

    return toFriendRequestDTO(request);
  }

  // Both sides of a newly formed friendship get an email; who clicked "accept" doesn't matter.
  private void sendFriendsNowEmails(User sender, User receiver) {
    emailNotificationService.sendBestEffort(
        sender.getEmail(),
        "You have a new friend on SEP",
        receiver.getName() + " is now your friend on SEP.");
    emailNotificationService.sendBestEffort(
        receiver.getEmail(),
        "You have a new friend on SEP",
        sender.getName() + " is now your friend on SEP.");
  }

  /**
   * Removes an existing friendship in both directions and deletes the accepted friend request that
   * created it, so a fresh request can be sent later.
   *
   * @param currentUsername username or email of the acting user
   * @param friendId id of the friend to remove
   * @throws ResponseStatusException 404 when the given user does not exist
   */
  @Transactional
  public void removeFriend(String currentUsername, long friendId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    User friend = getUserOrThrow(friendId);

    currentUser.getFriends().removeIf(existingFriend -> existingFriend.getId() == friend.getId());
    friend.getFriends().removeIf(existingFriend -> existingFriend.getId() == currentUser.getId());

    userRepository.save(currentUser);
    userRepository.save(friend);

    // alte accepted friendrequests entfernen
    friendRequestRepository
        .findBySenderAndReceiverAndStatus(currentUser, friend, FriendRequestStatus.ACCEPTED)
        .ifPresent(friendRequestRepository::delete);
    friendRequestRepository
        .findBySenderAndReceiverAndStatus(friend, currentUser, FriendRequestStatus.ACCEPTED)
        .ifPresent(friendRequestRepository::delete);

    notificationService.notifyFriendRemoved(friend.getName(), currentUser.getId());

    // Only the acting user is emailed; the removed friend is deliberately not notified.
    emailNotificationService.sendBestEffort(
        currentUser.getEmail(),
        "Friend removed",
        "You removed " + friend.getName() + " from your friends on SEP.");
  }

  private User getCurrentUserOrThrow(String username) {
    return userRepository
        .findByNameIgnoreCaseOrEmailIgnoreCase(username, username)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
  }

  private User getUserOrThrow(long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }

  private boolean isConnected(User user, User possibleFriend) {
    return user.getFriends().stream().anyMatch(friend -> friend.getId() == possibleFriend.getId());
  }

  private FriendDTO toFriendDTO(User user, boolean connected) {
    return new FriendDTO(
        user.getId(), user.getName(), user.getFirstName(), user.getSurname(), connected);
  }

  private FriendRequestDTO toFriendRequestDTO(FriendRequest request) {
    return new FriendRequestDTO(
        request.getId(),
        request.getSender().getId(),
        request.getSender().getName(),
        request.getReceiver().getId(),
        request.getReceiver().getName(),
        request.getStatus());
  }
}
