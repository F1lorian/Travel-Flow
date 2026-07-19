package com.sep.sep_backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for the user-trip membership table. */
@Embeddable
public class UserTripId implements Serializable {
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "trip_id")
  private Long tripId;

  public UserTripId() {}

  public UserTripId(Long userId, Long tripId) {
    this.userId = userId;
    this.tripId = tripId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getTripId() {
    return tripId;
  }

  public void setTripId(Long tripId) {
    this.tripId = tripId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof UserTripId that)) {
      return false;
    }
    return Objects.equals(userId, that.userId) && Objects.equals(tripId, that.tripId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, tripId);
  }
}
