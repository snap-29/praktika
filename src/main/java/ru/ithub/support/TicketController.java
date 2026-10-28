package ru.ithub.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.util.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.ithub.support.Ticket.*;

@RestController
@RequestMapping("/api")
@Validated
public class TicketController {
  private final TicketService service;
  private final TicketStore store;
  private final Clock clock;

  public TicketController(TicketService service, TicketStore store, Clock clock) {
    this.service = service;
    this.store = store;
    this.clock = clock;
  }

  @PostMapping("/tickets")
  public ResponseEntity<Ticket> create(@Valid @RequestBody Requests.Create r) {
    Ticket t = service.create(r);
    return ResponseEntity.created(java.net.URI.create("/api/tickets/" + t.id())).body(t);
  }

  @GetMapping("/tickets/{id}")
  public Ticket get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/tickets/{id}")
  public Ticket edit(@PathVariable UUID id, @Valid @RequestBody Requests.Edit r) {
    return service.edit(id, r);
  }

  @PatchMapping("/tickets/{id}/status")
  public Ticket status(@PathVariable UUID id, @Valid @RequestBody Requests.ChangeStatus r) {
    return service.change(id, r.status());
  }

  @GetMapping("/tickets")
  public TicketStore.Page list(
      @RequestParam(required = false) Status status,
      @RequestParam(required = false) Priority priority,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Boolean overdue,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return store.list(status, priority, categoryId, overdue, page, size, clock.instant());
  }

  @GetMapping("/categories")
  public List<TicketStore.Category> categories() {
    return store.categories();
  }

  @GetMapping("/tickets/{id}/history")
  public List<TicketStore.History> history(@PathVariable UUID id) {
    service.get(id);
    return store.history(id);
  }
}
