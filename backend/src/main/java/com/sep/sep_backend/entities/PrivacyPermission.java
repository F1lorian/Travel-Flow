package com.sep.sep_backend.entities;

import jakarta.persistence.*;

/**
 * Stores privacy permissions from one user to one specific friend.
 *
 * <p>A normal friendship only tells us that two users are connected. This entity defines which
 * information the friend is allowed to see.
 *
 * <p>Example: owner = Mike, friend = Louie, canSeeEvents = true, canSeeProfile = false means that
 * Louie can see Mike's events but not Mike's profile information.
 */
@Entity
@Table(
    name = "privacy_permissions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "friend_id"}))
public class PrivacyPermission {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  /**
   * The user whose information is protected.
   *
   * <p>This is the owner of the privacy settings.
   */
  @ManyToOne(optional = false)
  @JoinColumn(name = "owner_id")
  private User owner;

  /** The friend who may receive access to selected information of the owner. */
  @ManyToOne(optional = false)
  @JoinColumn(name = "friend_id")
  private User friend;

  /** Defines whether the friend is allowed to see the owner's events. */
  @Column(nullable = false)
  private boolean canSeeEvents = false;

  /** Defines whether the friend is allowed to see the owner's profile information. */
  @Column(nullable = false)
  private boolean canSeeProfile = false;

  public PrivacyPermission() {}

  public PrivacyPermission(User owner, User friend) {
    this.owner = owner;
    this.friend = friend;
  }

  public long getId() {
    return id;
  }

  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    this.owner = owner;
  }

  public User getFriend() {
    return friend;
  }

  public void setFriend(User friend) {
    this.friend = friend;
  }

  public boolean isCanSeeEvents() {
    return canSeeEvents;
  }

  public void setCanSeeEvents(boolean canSeeEvents) {
    this.canSeeEvents = canSeeEvents;
  }

  public boolean isCanSeeProfile() {
    return canSeeProfile;
  }

  public void setCanSeeProfile(boolean canSeeProfile) {
    this.canSeeProfile = canSeeProfile;
  }
}
