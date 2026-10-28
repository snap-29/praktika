package ru.ithub.support;

import static ru.ithub.support.Ticket.Status.*;

import ru.ithub.support.Ticket.Status;

public final class Workflow {
  private Workflow() {}

  public static boolean canTransition(Status from, Status to) {
    return (from == NEW && (to == IN_PROGRESS || to == CANCELLED))
        || (from == IN_PROGRESS && (to == RESOLVED || to == CANCELLED));
  }

  public static boolean editable(Status status) {
    return status == NEW || status == IN_PROGRESS;
  }
}
