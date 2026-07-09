package com.sep.sep_backend.entities;

import com.sep.sep_backend.friends.FriendRequestStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "friend_requests")
public class FriendRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "sender_id")
  private User sender;

  @ManyToOne(optional = false)
  @JoinColumn(name = "receiver_id")
  private User receiver;

  @Enumerated(EnumType.STRING)
  private FriendRequestStatus status = FriendRequestStatus.PENDING;

  private Instant createdAt = Instant.now();

  private Instant respondedAt;

  protected FriendRequest() {} // JPA

  public FriendRequest(User sender, User receiver) {
    this.sender = sender;
    this.receiver = receiver;
  }

  public long getId() {
    return id;
  }

  public User getSender() {
    return sender;
  }

  public User getReceiver() {
    return receiver;
  }

  public FriendRequestStatus getStatus() {
    return status;
  }

  public void setStatus(FriendRequestStatus status) {
    this.status = status;
    this.respondedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }
}
