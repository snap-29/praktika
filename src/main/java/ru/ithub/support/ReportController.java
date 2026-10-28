package ru.ithub.support;

import java.time.Instant;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public Summary summary(@RequestParam Instant from, @RequestParam Instant to) {
    return service.summary(from, to);
  }
}
