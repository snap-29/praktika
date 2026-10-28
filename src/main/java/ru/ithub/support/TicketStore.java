package ru.ithub.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ithub.support.Ticket.*;

@Repository
public class TicketStore implements ReportSource {
  private final JdbcTemplate jdbc;

  public TicketStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant instant(ResultSet rs, String key) throws SQLException {
    Timestamp t = rs.getTimestamp(key);
    return t == null ? null : t.toInstant();
  }

  private static Ticket map(ResultSet r, int row) throws SQLException {
    return new Ticket(
        r.getObject("id", UUID.class),
        r.getString("title"),
        r.getString("description"),
        r.getLong("category_id"),
        Priority.valueOf(r.getString("priority")),
        Status.valueOf(r.getString("status")),
        instant(r, "created_at"),
        instant(r, "due_at"),
        instant(r, "closed_at"));
  }

  private static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }

  public Optional<Ticket> find(UUID id, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM tickets WHERE id=?" + (lock ? " FOR UPDATE" : ""), TicketStore::map, id)
        .stream()
        .findFirst();
  }

  public boolean categoryExists(Long id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM categories WHERE id=?)", Boolean.class, id));
  }

  public void insert(Ticket t) {
    jdbc.update(
        "INSERT INTO tickets VALUES (?,?,?,?,?,?,?,?,?)",
        t.id(),
        t.title(),
        t.description(),
        t.categoryId(),
        t.priority().name(),
        t.status().name(),
        ts(t.createdAt()),
        ts(t.dueAt()),
        ts(t.closedAt()));
  }

  public void edit(UUID id, Requests.Edit e) {
    jdbc.update(
        "UPDATE tickets SET description=?,priority=?,due_at=? WHERE id=?",
        e.description(),
        e.priority().name(),
        ts(e.dueAt()),
        id);
  }

  public void status(UUID id, Status status, Instant closed) {
    jdbc.update(
        "UPDATE tickets SET status=?,closed_at=? WHERE id=?", status.name(), ts(closed), id);
  }

  public void history(UUID id, Status from, Status to, Instant at) {
    jdbc.update(
        "INSERT INTO status_history(ticket_id,from_status,to_status,changed_at) VALUES (?,?,?,?)",
        id,
        from == null ? null : from.name(),
        to.name(),
        ts(at));
  }

  public record Category(long id, String name) {}

  public record History(long id, Status fromStatus, Status toStatus, Instant changedAt) {}

  public List<Category> categories() {
    return jdbc.query(
        "SELECT * FROM categories ORDER BY id",
        (r, n) -> new Category(r.getLong("id"), r.getString("name")));
  }

  public List<History> history(UUID id) {
    return jdbc.query(
        "SELECT * FROM status_history WHERE ticket_id=? ORDER BY id",
        (r, n) ->
            new History(
                r.getLong("id"),
                r.getString("from_status") == null
                    ? null
                    : Status.valueOf(r.getString("from_status")),
                Status.valueOf(r.getString("to_status")),
                instant(r, "changed_at")),
        id);
  }

  public record Page(List<Ticket> content, long totalElements, int page, int size) {}

  public Page list(
      Status status,
      Priority priority,
      Long categoryId,
      Boolean overdue,
      int page,
      int size,
      Instant now) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (status != null) {
      where.append(" AND status=?");
      args.add(status.name());
    }
    if (priority != null) {
      where.append(" AND priority=?");
      args.add(priority.name());
    }
    if (categoryId != null) {
      where.append(" AND category_id=?");
      args.add(categoryId);
    }
    if (overdue != null) {
      where.append(overdue ? " AND " : " AND NOT ");
      where.append("(status IN ('NEW','IN_PROGRESS') AND due_at IS NOT NULL AND due_at < ?)");
      args.add(ts(now));
    }
    long total =
        Objects.requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM tickets" + where, Long.class, args.toArray()));
    args.add(size);
    args.add((long) page * size);
    return new Page(
        jdbc.query(
            "SELECT * FROM tickets" + where + " ORDER BY created_at,id LIMIT ? OFFSET ?",
            TicketStore::map,
            args.toArray()),
        total,
        page,
        size);
  }

  @Override
  public List<Ticket> createdBetween(Instant from, Instant to) {
    return jdbc.query(
        "SELECT * FROM tickets WHERE created_at>=? AND created_at<?",
        TicketStore::map,
        ts(from),
        ts(to));
  }
}
