package ru.ithub.support;

import java.time.Instant;
import java.util.UUID;

public record Ticket(
    UUID id,
    String title,
    String description,
    Long categoryId,
    Priority priority,
    Status status,
    Instant createdAt,
    Instant dueAt,
    Instant closedAt) {
  public enum Priority {
    LOW,
    MEDIUM,
    HIGH
  }

  public enum Status {
    NEW,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED
  }

  public boolean isOverdue(Instant now) {
    return (status == Status.NEW || status == Status.IN_PROGRESS)
        && dueAt != null
        && dueAt.isBefore(now);
  }
}
