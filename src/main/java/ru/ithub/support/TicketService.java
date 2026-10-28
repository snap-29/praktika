package ru.ithub.support;

import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ithub.support.Ticket.*;

@Service
public class TicketService {
  private final TicketStore store;
  private final Clock clock;

  public TicketService(TicketStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  public Ticket get(UUID id) {
    return required(id, false);
  }

  private Ticket required(UUID id, boolean lock) {
    return store.find(id, lock).orElseThrow(() -> new ApiException(404, "Ticket not found"));
  }

  @Transactional
  public Ticket create(Requests.Create r) {
    if (!store.categoryExists(r.categoryId())) throw new ApiException(400, "Unknown category");
    Ticket t =
        new Ticket(
            UUID.randomUUID(),
            r.title(),
            r.description(),
            r.categoryId(),
            r.priority(),
            Status.NEW,
            clock.instant(),
            r.dueAt(),
            null);
    store.insert(t);
    store.history(t.id(), null, Status.NEW, t.createdAt());
    return t;
  }

  @Transactional
  public Ticket edit(UUID id, Requests.Edit r) {
    Ticket t = required(id, true);
    if (!Workflow.editable(t.status()))
      throw new ApiException(409, "Closed ticket cannot be edited");
    store.edit(id, r);
    return get(id);
  }

  @Transactional
  public Ticket change(UUID id, Status status) {
    Ticket t = required(id, true);
    if (!Workflow.canTransition(t.status(), status))
      throw new ApiException(409, "Forbidden status transition");
    Instant now = clock.instant();
    store.status(id, status, Workflow.editable(status) ? null : now);
    store.history(id, t.status(), status, now);
    return get(id);
  }
}
