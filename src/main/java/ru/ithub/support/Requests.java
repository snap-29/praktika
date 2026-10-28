package ru.ithub.support;

import jakarta.validation.constraints.*;
import java.time.Instant;
import ru.ithub.support.Ticket.*;

public final class Requests {
  private Requests() {}

  public record Create(
      @NotBlank @Size(max = 200) String title,
      @NotNull @Size(max = 10000) String description,
      @NotNull Long categoryId,
      @NotNull Priority priority,
      Instant dueAt) {}

  public record Edit(
      @NotNull @Size(max = 10000) String description, @NotNull Priority priority, Instant dueAt) {}

  public record ChangeStatus(@NotNull Status status) {}
}
