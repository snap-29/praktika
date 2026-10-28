package ru.ithub.support;

import java.time.Instant;
import java.util.List;

public interface ReportSource {
  List<Ticket> createdBetween(Instant from, Instant to);
}
