package ru.ithub.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.ithub.support.Ticket.*;

class BusinessTest {
  final Instant now = Instant.parse("2026-01-01T12:00:00Z");
  final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

  Ticket ticket(Status s, Instant due) {
    return new Ticket(
        UUID.randomUUID(),
        "Printer",
        "Broken",
        1L,
        Priority.HIGH,
        s,
        now.minusSeconds(60),
        due,
        s == Status.RESOLVED || s == Status.CANCELLED ? now : null);
  }

  @Test
  void newToProgress() {
    assertThat(Workflow.canTransition(Status.NEW, Status.IN_PROGRESS)).isTrue();
  }

  @Test
  void progressToResolved() {
    assertThat(Workflow.canTransition(Status.IN_PROGRESS, Status.RESOLVED)).isTrue();
  }

  @Test
  void newCancellation() {
    assertThat(Workflow.canTransition(Status.NEW, Status.CANCELLED)).isTrue();
  }

  @Test
  void progressCancellation() {
    assertThat(Workflow.canTransition(Status.IN_PROGRESS, Status.CANCELLED)).isTrue();
  }

  @Test
  void cannotSkipProgress() {
    assertThat(Workflow.canTransition(Status.NEW, Status.RESOLVED)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(Status.class)
  void terminalCannotReopen(Status target) {
    assertThat(Workflow.canTransition(Status.RESOLVED, target)).isFalse();
    assertThat(Workflow.canTransition(Status.CANCELLED, target)).isFalse();
  }

  @Test
  void exactDueIsNotOverdueRegression() {
    assertThat(ticket(Status.NEW, now).isOverdue(now)).isFalse();
  }

  @Test
  void pastDueIsOverdue() {
    assertThat(ticket(Status.IN_PROGRESS, now.minusNanos(1)).isOverdue(now)).isTrue();
  }

  @Test
  void terminalNotOverdue() {
    assertThat(ticket(Status.RESOLVED, now.minusSeconds(1)).isOverdue(now)).isFalse();
  }

  @Test
  void absentDeadlineNotOverdue() {
    assertThat(ticket(Status.NEW, null).isOverdue(now)).isFalse();
  }

  @Test
  void unknownTicket404() {
    TicketStore store = mock(TicketStore.class);
    UUID id = UUID.randomUUID();
    when(store.find(id, false)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> new TicketService(store, clock).get(id))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(404));
  }

  @Test
  void createIsNewAndHistoryRecorded() {
    TicketStore store = mock(TicketStore.class);
    when(store.categoryExists(1L)).thenReturn(true);
    Ticket t =
        new TicketService(store, clock)
            .create(new Requests.Create("Printer", "Broken", 1L, Priority.HIGH, null));
    assertThat(t.status()).isEqualTo(Status.NEW);
    assertThat(t.createdAt()).isEqualTo(now);
    verify(store).history(t.id(), null, Status.NEW, now);
  }

  @Test
  void closedEditDoesNotWrite() {
    TicketStore store = mock(TicketStore.class);
    Ticket t = ticket(Status.RESOLVED, null);
    when(store.find(t.id(), true)).thenReturn(Optional.of(t));
    assertThatThrownBy(
            () ->
                new TicketService(store, clock)
                    .edit(t.id(), new Requests.Edit("x", Priority.LOW, null)))
        .isInstanceOf(ApiException.class);
    verify(store, never()).edit(any(), any());
  }

  @Test
  void blankTitleInvalid() {
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      assertThat(
              factory
                  .getValidator()
                  .validate(new Requests.Create(" ", "x", null, Priority.LOW, null)))
          .hasSize(2);
    }
  }

  @Test
  void unknownCategoryRejectedBeforeInsert() {
    TicketStore store = mock(TicketStore.class);
    assertThatThrownBy(
            () ->
                new TicketService(store, clock)
                    .create(new Requests.Create("Printer", "Broken", 99L, Priority.HIGH, null)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(400));
    verify(store, never()).insert(any());
    verify(store, never()).history(any(), any(), any(), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = Status.class,
      names = {"IN_PROGRESS", "RESOLVED"})
  void acceptedTransitionSetsClosureAndHistory(Status target) {
    TicketStore store = mock(TicketStore.class);
    Ticket original = ticket(target == Status.IN_PROGRESS ? Status.NEW : Status.IN_PROGRESS, null);
    Instant closed = target == Status.RESOLVED ? now : null;
    Ticket updated =
        new Ticket(
            original.id(),
            original.title(),
            original.description(),
            original.categoryId(),
            original.priority(),
            target,
            original.createdAt(),
            null,
            closed);
    when(store.find(original.id(), true)).thenReturn(Optional.of(original));
    when(store.find(original.id(), false)).thenReturn(Optional.of(updated));
    assertThat(new TicketService(store, clock).change(original.id(), target)).isEqualTo(updated);
    verify(store).status(original.id(), target, closed);
    verify(store).history(original.id(), original.status(), target, now);
  }

  @Test
  void forbiddenTransitionDoesNotWrite() {
    TicketStore store = mock(TicketStore.class);
    Ticket t = ticket(Status.RESOLVED, null);
    when(store.find(t.id(), true)).thenReturn(Optional.of(t));
    assertThatThrownBy(() -> new TicketService(store, clock).change(t.id(), Status.IN_PROGRESS))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(409));
    verify(store, never()).status(any(), any(), any());
    verify(store, never()).history(any(), any(), any(), any());
  }

  @Test
  void openEditReturnsPersistedTicket() {
    TicketStore store = mock(TicketStore.class);
    Ticket t = ticket(Status.NEW, null);
    Requests.Edit edit = new Requests.Edit("Changed", Priority.LOW, null);
    when(store.find(t.id(), true)).thenReturn(Optional.of(t));
    when(store.find(t.id(), false)).thenReturn(Optional.of(t));
    assertThat(new TicketService(store, clock).edit(t.id(), edit)).isEqualTo(t);
    verify(store).edit(t.id(), edit);
  }
}
