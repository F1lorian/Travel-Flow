package com.sep.sep_backend.Enitys;

import jakarta.persistence.*;

@Entity
@Table(name = "user_events")
public class UserEvent {

    public enum InviteStatus {
        PENDING,
        ACCEPTED,
        DECLINED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // der Eingeladene / Teilnehmer

    @ManyToOne
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator; // wer diese Einladung verschickt hat

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteStatus status = InviteStatus.PENDING;

    public UserEvent() {}

    public UserEvent(Event event, User user, User creator) {
        this.event = event;
        this.user = user;
        this.creator = creator;
    }

    public long getId() { return id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }
    public InviteStatus getStatus() { return status; }
    public void setStatus(InviteStatus status) { this.status = status; }
}