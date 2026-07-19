package com.sep.sep_backend.friends;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for searching friends and managing friend requests. */
@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201"})
@RequestMapping("/api/friends")
public class FriendsController {
  private final FriendsService friendsService;

  public FriendsController(FriendsService friendsService) {
    this.friendsService = friendsService;
  }

  /** Returns all accepted friends of the authenticated user. */
  @GetMapping
  public List<FriendDTO> getFriends(Authentication authentication) {
    return friendsService.getFriends(getCurrentUsername(authentication));
  }

  /** Searches users by name and marks whether each result is already connected. */
  @GetMapping("/search")
  public List<FriendDTO> searchUsers(@RequestParam String name, Authentication authentication) {
    return friendsService.searchUsers(getCurrentUsername(authentication), name);
  }

  /** Removes an existing friendship for the authenticated user. */
  @DeleteMapping("/{userId}")
  public void removeFriend(@PathVariable long userId, Authentication authentication) {
    friendsService.removeFriend(getCurrentUsername(authentication), userId);
  }

  /** Sends a friend request from the authenticated user to the target user. */
  @PostMapping("/requests/{userId}")
  public FriendRequestDTO sendFriendRequest(
      @PathVariable long userId, Authentication authentication) {
    return friendsService.sendFriendRequest(getCurrentUsername(authentication), userId);
  }

  /** Accepts an incoming friend request addressed to the authenticated user. */
  @PostMapping("/requests/{requestId}/accept")
  public FriendRequestDTO acceptFriendRequest(
      @PathVariable long requestId, Authentication authentication) {
    return friendsService.acceptFriendRequest(getCurrentUsername(authentication), requestId);
  }

  /** Declines an incoming friend request addressed to the authenticated user. */
  @PostMapping("/requests/{requestId}/decline")
  public FriendRequestDTO declineFriendRequest(
      @PathVariable long requestId, Authentication authentication) {
    return friendsService.declineFriendRequest(getCurrentUsername(authentication), requestId);
  }

  /** Lists pending friend requests received by the authenticated user. */
  @GetMapping("/requests/incoming")
  public List<FriendRequestDTO> getIncomingRequests(Authentication authentication) {
    return friendsService.getIncomingRequests(getCurrentUsername(authentication));
  }

  /** Lists pending friend requests sent by the authenticated user. */
  @GetMapping("/requests/outgoing")
  public List<FriendRequestDTO> getOutgoingRequests(Authentication authentication) {
    return friendsService.getOutgoingRequests(getCurrentUsername(authentication));
  }

  /** Resolves the authenticated username from the Spring Security context. */
  private String getCurrentUsername(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
    }

    return authentication.getName();
  }
}
