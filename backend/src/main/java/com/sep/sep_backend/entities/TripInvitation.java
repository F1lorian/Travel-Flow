package com.sep.sep_backend.entities;

import com.sep.sep_backend.friends.FriendRequestStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class TripInvitation {
  @Id @GeneratedValue private Long id;

  @ManyToOne private Trip trip;

  @ManyToOne private User invitedBy;

  @ManyToOne private User invitee;

  @Enumerated(EnumType.STRING)
  private FriendRequestStatus status = FriendRequestStatus.PENDING;

  private Instant createdAt;
  private Instant respondedAt;
}
