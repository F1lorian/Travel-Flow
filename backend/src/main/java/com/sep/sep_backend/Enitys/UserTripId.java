package com.sep.sep_backend.Enitys;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserTripId implements Serializable {
  private Long userId;
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
