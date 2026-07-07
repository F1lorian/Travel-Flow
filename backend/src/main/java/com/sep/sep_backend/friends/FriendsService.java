package com.sep.sep_backend.friends;

import com.sep.sep_backend.entities.FriendRequest;
import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.repos.FriendRequestRepository;
import com.sep.sep_backend.repos.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FriendsService {
  private final UserRepository userRepository;
  private final FriendRequestRepository friendRequestRepository;
  private final FriendRequestNotificationService notificationService;

  public FriendsService(
      UserRepository userRepository,
      FriendRequestRepository friendRequestRepository,
      FriendRequestNotificationService notificationService) {
    this.userRepository = userRepository;
    this.friendRequestRepository = friendRequestRepository;
    this.notificationService = notificationService;
  }

  public List<FriendDTO> getFriends(String currentUsername) {
    User currentUser = getCurrentUserOrThrow(currentUsername);

    return currentUser.getFriends().stream().map(friend -> toFriendDTO(friend, true)).toList();
  }

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
      return dto;
    }

    FriendRequest request = new FriendRequest(sender, receiver);
    friendRequestRepository.save(request);

    FriendRequestDTO dto = toFriendRequestDTO(request);
    notificationService.notifyNewRequest(dto);
    return dto;
  }

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
    return dto;
  }

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
    return dto;
  }

  public List<FriendRequestDTO> getIncomingRequests(String currentUsername) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    return friendRequestRepository
        .findByReceiverAndStatus(currentUser, FriendRequestStatus.PENDING)
        .stream()
        .map(this::toFriendRequestDTO)
        .toList();
  }

  public List<FriendRequestDTO> getOutgoingRequests(String currentUsername) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    return friendRequestRepository
        .findBySenderAndStatus(currentUser, FriendRequestStatus.PENDING)
        .stream()
        .map(this::toFriendRequestDTO)
        .toList();
  }

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
