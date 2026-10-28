package ru.ithub.support;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class ApiIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16.6-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM status_history");
    jdbc.update("DELETE FROM tickets");
  }

  String create() throws Exception {
    return mapper
        .readTree(
            mvc.perform(
                    post("/api/tickets")
                        .contentType("application/json")
                        .content(
                            """
   {"title":"Printer","description":"Broken","categoryId":1,"priority":"HIGH","dueAt":"2026-01-01T11:00:00Z"}
   """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asText();
  }

  void change(String id, String state, int expected) throws Exception {
    mvc.perform(
            patch("/api/tickets/" + id + "/status")
                .contentType("application/json")
                .content("{\"status\":\"" + state + "\"}"))
        .andExpect(status().is(expected));
  }

  @Test
  void migrationsAndCategories() throws Exception {
    mvc.perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void createReadAndInitialHistory() throws Exception {
    String id = create();
    mvc.perform(get("/api/tickets/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NEW"));
    mvc.perform(get("/api/tickets/" + id + "/history"))
        .andExpect(jsonPath("$[0].toStatus").value("NEW"));
  }

  @Test
  void filteringAndPagination() throws Exception {
    create();
    create();
    mvc.perform(
            get("/api/tickets")
                .param("priority", "HIGH")
                .param("status", "NEW")
                .param("categoryId", "1")
                .param("overdue", "true")
                .param("size", "1")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(1));
    mvc.perform(get("/api/tickets").param("overdue", "false"))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void validation400() throws Exception {
    mvc.perform(post("/api/tickets").contentType("application/json").content("{\"title\":\" \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/tickets").param("size", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/tickets").param("priority", "URGENT")).andExpect(status().isBadRequest());
  }

  @Test
  void missing404() throws Exception {
    mvc.perform(get("/api/tickets/" + UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void editsPersist() throws Exception {
    String id = create();
    mvc.perform(
            put("/api/tickets/" + id)
                .contentType("application/json")
                .content("{\"description\":\"Fixed soon\",\"priority\":\"LOW\",\"dueAt\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priority").value("LOW"))
        .andExpect(jsonPath("$.description").value("Fixed soon"));
  }

  @Test
  void reportHalfOpenIntervalAndInvalidPeriod() throws Exception {
    String id = create();
    change(id, "IN_PROGRESS", 200);
    change(id, "RESOLVED", 200);
    mvc.perform(
            get("/api/reports/summary")
                .param("from", "2026-01-01T12:00:00Z")
                .param("to", "2026-01-01T12:00:01Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdCount").value(1))
        .andExpect(jsonPath("$.averageResolutionSeconds").value(0.0));
    mvc.perform(
            get("/api/reports/summary")
                .param("from", "2026-01-01T11:00:00Z")
                .param("to", "2026-01-01T12:00:00Z"))
        .andExpect(jsonPath("$.createdCount").value(0));
    mvc.perform(
            get("/api/reports/summary")
                .param("from", "2026-01-01T12:00:00Z")
                .param("to", "2026-01-01T12:00:00Z"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void historyFailureRollsBackStatus() throws Exception {
    String id = create();
    jdbc.execute(
        "ALTER TABLE status_history ADD CONSTRAINT test_failure CHECK(to_status <> 'IN_PROGRESS')");
    try {
      change(id, "IN_PROGRESS", 500);
      mvc.perform(get("/api/tickets/" + id)).andExpect(jsonPath("$.status").value("NEW"));
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM status_history WHERE ticket_id=?::uuid", Integer.class, id))
          .isEqualTo(1);
    } finally {
      jdbc.execute("ALTER TABLE status_history DROP CONSTRAINT test_failure");
    }
  }

  @Test
  @Tag("e2e")
  void completeLifecycle() throws Exception {
    String id = create();
    change(id, "IN_PROGRESS", 200);
    change(id, "RESOLVED", 200);
    mvc.perform(get("/api/tickets/" + id))
        .andExpect(jsonPath("$.status").value("RESOLVED"))
        .andExpect(jsonPath("$.closedAt").value("2026-01-01T12:00:00Z"));
    mvc.perform(get("/api/tickets/" + id + "/history")).andExpect(jsonPath("$.length()").value(3));
    mvc.perform(
            get("/api/reports/summary")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-01-02T00:00:00Z"))
        .andExpect(jsonPath("$.byStatus.RESOLVED").value(1));
  }

  @Test
  @Tag("e2e")
  void forbiddenOperationLeavesDataUnchanged() throws Exception {
    String id = create();
    change(id, "CANCELLED", 200);
    String before =
        mvc.perform(get("/api/tickets/" + id)).andReturn().getResponse().getContentAsString();
    String history =
        mvc.perform(get("/api/tickets/" + id + "/history"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    change(id, "IN_PROGRESS", 409);
    mvc.perform(
            put("/api/tickets/" + id)
                .contentType("application/json")
                .content("{\"description\":\"changed\",\"priority\":\"LOW\"}"))
        .andExpect(status().isConflict());
    assertThat(
            mvc.perform(get("/api/tickets/" + id)).andReturn().getResponse().getContentAsString())
        .isEqualTo(before);
    assertThat(
            mvc.perform(get("/api/tickets/" + id + "/history"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .isEqualTo(history);
  }
}
