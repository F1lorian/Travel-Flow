package com.sep.sep_backend.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "user_trips")
public class UserTrip {

  public enum InviteStatus {
    PENDING,
    ACCEPTED,
    DECLINED
  }

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

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InviteStatus status = InviteStatus.PENDING;

  @Column(nullable = false, columnDefinition = "double precision default 0")
  private double budget = 0;

  @Column(nullable = false, columnDefinition = "double precision default 0")
  private double warningRange = 0;

  public UserTrip() {}

  public UserTrip(User user, Trip trip, User invitedBy) {
    this.user = user;
    this.trip = trip;
    this.invitedBy = invitedBy;
    this.id = new UserTripId(user.getId(), trip.getId());
  }

  public UserTrip(User user, Trip trip, User invitedBy, InviteStatus status) {
    this(user, trip, invitedBy);
    this.status = status;
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

  public double getBudget() {
    return budget;
  }

  public void setInvitedBy(User invitedBy) {
    this.invitedBy = invitedBy;
  }

  public void setBudget(double budget) {
    this.budget = budget;
  }

  public double getWarningRange() {
    return warningRange;
  }

  public void setWarningRange(double warningRange) {
    this.warningRange = warningRange;
  }

  public InviteStatus getStatus() {
    return status;
  }

  public void setStatus(InviteStatus status) {
    this.status = status;
  }
}
