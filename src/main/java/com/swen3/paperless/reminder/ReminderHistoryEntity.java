package com.swen3.paperless.reminder;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reminder_history")
public class ReminderHistoryEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reminder_id", nullable = false)
    private ReminderEntity reminder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReminderEvent event;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ReminderHistoryEntity() {
    }

    public ReminderHistoryEntity(ReminderEntity reminder, ReminderEvent event, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.reminder = reminder;
        this.event = event;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ReminderEntity getReminder() { return reminder; }
    public void setReminder(ReminderEntity reminder) { this.reminder = reminder; }
    public ReminderEvent getEvent() { return event; }
    public void setEvent(ReminderEvent event) { this.event = event; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
