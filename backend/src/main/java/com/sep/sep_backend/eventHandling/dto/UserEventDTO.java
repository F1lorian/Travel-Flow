package com.sep.sep_backend.eventHandling.dto;

import com.sep.sep_backend.entities.UserEvent;

/**
 * DTO for UserEventConnections before they are saved in the database
 */
public class UserEventDTO {
  private Long userId;
  private String username;
  private String firstname;
  private String surname;
  private UserEvent.InviteStatus status;

  public UserEventDTO(
      Long userId,
      String username,
      String firstname,
      String surname,
      UserEvent.InviteStatus status) {
    this.userId = userId;
    this.username = username;
    this.firstname = firstname;
    this.surname = surname;
    this.status = status;
  }

  public static UserEventDTO from(UserEvent userEvent) {
    return new UserEventDTO(
        userEvent.getUser().getId(),
        userEvent.getUser().getName(),
        userEvent.getUser().getFirstName(),
        userEvent.getUser().getSurname(),
        userEvent.getStatus());
  }

  public Long getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getFirstname() {
    return firstname;
  }

  public String getSurname() {
    return surname;
  }

  public UserEvent.InviteStatus getStatus() {
    return status;
  }
}
