package com.sep.sep_backend.friends;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201"})
@RequestMapping("/api/friends")
public class FriendsController {
  private final FriendsService friendsService;

  public FriendsController(FriendsService friendsService) {
    this.friendsService = friendsService;
  }

  @GetMapping
  public List<FriendDTO> getFriends(Authentication authentication) {
    return friendsService.getFriends(getCurrentUsername(authentication));
  }

  @GetMapping("/search")
  public List<FriendDTO> searchUsers(@RequestParam String name, Authentication authentication) {
    return friendsService.searchUsers(getCurrentUsername(authentication), name);
  }

  @DeleteMapping("/{userId}")
  public void removeFriend(@PathVariable long userId, Authentication authentication) {
    friendsService.removeFriend(getCurrentUsername(authentication), userId);
  }

  @PostMapping("/requests/{userId}")
  public FriendRequestDTO sendFriendRequest(
      @PathVariable long userId, Authentication authentication) {
    return friendsService.sendFriendRequest(getCurrentUsername(authentication), userId);
  }

  @PostMapping("/requests/{requestId}/accept")
  public FriendRequestDTO acceptFriendRequest(
      @PathVariable long requestId, Authentication authentication) {
    return friendsService.acceptFriendRequest(getCurrentUsername(authentication), requestId);
  }

  @PostMapping("/requests/{requestId}/decline")
  public FriendRequestDTO declineFriendRequest(
      @PathVariable long requestId, Authentication authentication) {
    return friendsService.declineFriendRequest(getCurrentUsername(authentication), requestId);
  }

  @GetMapping("/requests/incoming")
  public List<FriendRequestDTO> getIncomingRequests(Authentication authentication) {
    return friendsService.getIncomingRequests(getCurrentUsername(authentication));
  }

  @GetMapping("/requests/outgoing")
  public List<FriendRequestDTO> getOutgoingRequests(Authentication authentication) {
    return friendsService.getOutgoingRequests(getCurrentUsername(authentication));
  }

  private String getCurrentUsername(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
    }

    return authentication.getName();
  }
}
