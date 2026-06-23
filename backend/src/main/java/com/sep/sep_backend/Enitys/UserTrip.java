package com.sep.sep_backend.Enitys;

import jakarta.persistence.*;

@Entity
@Table(name = "user_trips")
public class UserTrip {
  @EmbeddedId private UserTripId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("userId")
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("tripId")
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invited_by_id")
  private User invitedBy;

  public UserTrip() {}

  public UserTrip(User user, Trip trip, User invitedBy) {
    this.user = user;
    this.trip = trip;
    this.invitedBy = invitedBy;
    this.id = new UserTripId(user.getId(), trip.getId());
  }

  public UserTripId getId() {
    return id;
  }

  public void setId(UserTripId id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip trip) {
    this.trip = trip;
  }

  public User getInvitedBy() {
    return invitedBy;
  }

  public void setInvitedBy(User invitedBy) {
    this.invitedBy = invitedBy;
  }
}
