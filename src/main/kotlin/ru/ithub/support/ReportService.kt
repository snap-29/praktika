package ru.ithub.support

import java.time.Clock
import java.time.Duration
import java.time.Instant
import org.springframework.stereotype.Service

data class Summary(
    val from: Instant,
    val to: Instant,
    val createdCount: Int,
    val byStatus: Map<Ticket.Status, Int>,
    val byPriority: Map<Ticket.Priority, Int>,
    val overdueOpenCount: Int,
    val averageResolutionSeconds: Double?,
)

@Service
class ReportService(private val source: ReportSource, private val clock: Clock) {
    fun summary(from: Instant, to: Instant): Summary {
        if (!from.isBefore(to)) throw ApiException(400, "from must be before to")
        val tickets = source.createdBetween(from, to)
        val now = clock.instant()
        val durations =
            tickets
                .filter { it.status() == Ticket.Status.RESOLVED }
                .mapNotNull { ticket ->
                    ticket.closedAt()?.let {
                        Duration.between(ticket.createdAt(), it).toMillis() / 1000.0
                    }
                }
        return Summary(
            from,
            to,
            tickets.size,
            Ticket.Status.entries.associateWith { status ->
                tickets.count { it.status() == status }
            },
            Ticket.Priority.entries.associateWith { priority ->
                tickets.count { it.priority() == priority }
            },
            tickets.count { it.isOverdue(now) },
            durations.takeIf { it.isNotEmpty() }?.average(),
        )
    }
}
