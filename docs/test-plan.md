# Тест-план и трассировка

Цель — проверить правила, атомарность, границы времени, контракт и эксплуатационные риски. Часы задаёт Clock.fixed; БД интеграционных тестов — исключительно PostgreSQL 16.6 Testcontainers. Перед каждым тестом очищаются заявки и история; категории создаёт Flyway. Нет disabledIfDockerUnavailable, H2 и скрытого пропуска обязательных тестов.

| Требования / истории | Проверки | Автотесты |
|---|---|---|
| FR1, US1–2 | 201, NEW, время, история; неизвестный ID 404 | BusinessTest.createIsNewAndHistoryRecorded, unknownTicket404; ApiIntegrationTest.createReadAndInitialHistory, missing404 |
| FR2, US3 | сохранение правок, запрет закрытой заявки | BusinessTest.closedEditDoesNotWrite; ApiIntegrationTest.editsPersist, forbiddenOperationLeavesDataUnchanged |
| FR3, US4 | все разрешённые направления, пропуск этапа, терминальные состояния | BusinessTest.newToProgress, progressToResolved, newCancellation, progressCancellation, cannotSkipProgress, terminalCannotReopen |
| FR4, US5, NFR6 | история и статус атомарны; принудительная ошибка INSERT истории | ApiIntegrationTest.historyFailureRollsBackStatus, completeLifecycle |
| FR5, US6 | совместные фильтры, страница, total, overdue=false | ApiIntegrationTest.filteringAndPagination |
| FR6, US7 | пусто, нет завершённых, среднее только RESOLVED, полуинтервал, неверный период | ReportServiceTest (4 метода), ApiIntegrationTest.reportHalfOpenIntervalAndInvalidPeriod |
| FR7, US8 | Flyway применён, категории существуют | ApiIntegrationTest.migrationsAndCategories |
| FR8 | обязательность и enum/пагинация 400 | BusinessTest.blankTitleInvalid; ApiIntegrationTest.validation400 |
| Просрочка | до/точно/после срока, null и терминальный статус | BusinessTest.exactDueIsNotOverdueRegression, pastDueIsOverdue, terminalNotOverdue, absentDeadlineNotOverdue |
| NFR1–2,7 | контейнер/health, перезапуск и восстановление | scripts/smoke.sh, verify-operations.sh, restore-test.sh |
| NFR8 | PR без deploy, ошибки блокируют публикацию, main доставляется | .github/workflows/ci.yaml и реальный запуск GitHub Actions |

Состав: Java unit — 18 методов, два параметризованы четырьмя и двумя значениями; Kotlin unit — 4 метода, проверяются также Java Workflow и Ticket. Интеграционные — 8 методов; два дополнительных сквозных сценария в том же классе помечены e2e. Задача integrationTest запускает оба тега; unit test их исключает. Сквозные: полный NEW → IN_PROGRESS → RESOLVED → отчёт; CANCELLED → запрещённая операция 409 без изменения объекта/истории.

Команды и отчёты — README. JaCoCo объединяет test.exec и integrationTest.exec, покрывает Java/Kotlin; анализировать отдельно Workflow, TicketService, Ticket.isOverdue и ReportService, не делать вывод по сгенерированным getter/data class. Реальные значения и ограничения — evidence.md. Не считать отсутствующий Docker успешным выполнением integrationTest.

Критерий выхода: все обязательные тесты проходят, дефект D1 покрыт регрессией, контейнерные проверки успешны, CD и откат показаны на стенде. Доказательства прикладываются после фактического выполнения.
