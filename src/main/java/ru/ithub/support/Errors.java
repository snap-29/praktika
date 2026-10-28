package ru.ithub.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice
public class Errors {
  private final Clock clock;

  public Errors(Clock clock) {
    this.clock = clock;
  }

  public record ErrorBody(Instant timestamp, int status, String message, String path) {}

  private ResponseEntity<ErrorBody> result(int code, String message, HttpServletRequest r) {
    return ResponseEntity.status(code)
        .body(new ErrorBody(clock.instant(), code, message, r.getRequestURI()));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorBody> api(ApiException e, HttpServletRequest r) {
    return result(e.status(), e.getMessage(), r);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ServletRequestBindingException.class,
    HandlerMethodValidationException.class
  })
  public ResponseEntity<ErrorBody> invalid(Exception e, HttpServletRequest r) {
    return result(400, "Invalid request: check required fields, values and formats", r);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest r) {
    LoggerFactory.getLogger(Errors.class).error("Request failed: {}", r.getRequestURI(), e);
    return result(500, "Internal server error", r);
  }
}
