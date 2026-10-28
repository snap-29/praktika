package ru.ithub.support

import java.time.*
import java.util.UUID
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class ReportServiceTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun ticket(status: Ticket.Status, closed: Instant? = null, due: Instant? = null) =
        Ticket(
            UUID.randomUUID(),
            "Test",
            "Body",
            1L,
            Ticket.Priority.HIGH,
            status,
            now.minusSeconds(120),
            due,
            closed,
        )

    @Test
    fun emptyReport() {
        val report =
            ReportService(ReportSource { _, _ -> emptyList() }, clock)
                .summary(now.minusSeconds(300), now)
        assertThat(report.createdCount).isZero()
        assertThat(report.averageResolutionSeconds).isNull()
        assertThat(report.byStatus.values.sum()).isZero()
    }

    @Test
    fun noResolvedReturnsNull() {
        val service =
            ReportService(
                ReportSource { _, _ ->
                    listOf(ticket(Ticket.Status.NEW, due = now.minusSeconds(1)))
                },
                clock,
            )
        val result = service.summary(now.minusSeconds(300), now)
        assertThat(result.averageResolutionSeconds).isNull()
        assertThat(result.overdueOpenCount).isEqualTo(1)
    }

    @Test
    fun calculatesResolvedOnlyAndUsesJavaComponent() {
        val data =
            listOf(
                ticket(Ticket.Status.RESOLVED, now),
                ticket(Ticket.Status.CANCELLED, now.minusSeconds(60)),
                ticket(Ticket.Status.NEW, due = now),
            )
        val report =
            ReportService(ReportSource { _, _ -> data }, clock).summary(now.minusSeconds(300), now)
        assertThat(report.averageResolutionSeconds).isEqualTo(120.0)
        assertThat(report.createdCount).isEqualTo(3)
        assertThat(report.overdueOpenCount).isZero()
        assertThat(Workflow.canTransition(Ticket.Status.NEW, Ticket.Status.IN_PROGRESS)).isTrue()
        assertThat(data.last().isOverdue(now)).isFalse()
    }

    @Test
    fun rejectsEqualAndReversePeriod() {
        val service = ReportService(ReportSource { _, _ -> error("must not query") }, clock)
        assertThatThrownBy { service.summary(now, now) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { service.summary(now, now.minusSeconds(1)) }
            .isInstanceOf(ApiException::class.java)
    }
}
