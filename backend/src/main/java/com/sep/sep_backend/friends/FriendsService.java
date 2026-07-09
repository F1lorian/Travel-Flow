package com.sep.sep_backend.friends;

import com.sep.sep_backend.entities.User;
import com.sep.sep_backend.notification.EmailNotificationService;
import com.sep.sep_backend.repos.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FriendsService {
  private final UserRepository userRepository;
  private final EmailNotificationService emailNotificationService;

  public FriendsService(
      UserRepository userRepository, EmailNotificationService emailNotificationService) {
    this.userRepository = userRepository;
    this.emailNotificationService = emailNotificationService;
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
  public FriendDTO addFriend(String currentUsername, long friendId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    User friend = getUserOrThrow(friendId);

    if (currentUser.getId() == friend.getId()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot add themselves");
    }

    if (!isConnected(currentUser, friend)) {
      currentUser.getFriends().add(friend);
      friend.getFriends().add(currentUser);

      userRepository.save(currentUser);
      userRepository.save(friend);

      // Friendship is symmetric, so the other user learns about it even while logged out.
      emailNotificationService.sendBestEffort(
          friend.getEmail(),
          "You have a new friend on SEP",
          currentUser.getName() + " added you as a friend on SEP.");
      // The acting user's in-app notification is mirrored to their inbox as well.
      emailNotificationService.sendBestEffort(
          currentUser.getEmail(),
          "Friend added",
          "You added " + friend.getName() + " as a friend on SEP.");
    }

    return toFriendDTO(friend, true);
  }

  @Transactional
  public void removeFriend(String currentUsername, long friendId) {
    User currentUser = getCurrentUserOrThrow(currentUsername);
    User friend = getUserOrThrow(friendId);

    boolean wasConnected = isConnected(currentUser, friend);
    currentUser.getFriends().removeIf(existingFriend -> existingFriend.getId() == friend.getId());
    friend.getFriends().removeIf(existingFriend -> existingFriend.getId() == currentUser.getId());

    userRepository.save(currentUser);
    userRepository.save(friend);

    // Only the acting user is emailed; the removed friend is deliberately not notified.
    if (wasConnected) {
      emailNotificationService.sendBestEffort(
          currentUser.getEmail(),
          "Friend removed",
          "You removed " + friend.getName() + " from your friends on SEP.");
    }
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
}
