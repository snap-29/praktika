# Демонстрация на 10–15 минут

1. (2 мин) US4/FR3 → Workflow.canTransition → BusinessTest.cannotSkipProgress. Показать 409 при NEW → RESOLVED.
2. (3 мин) Запустить Compose, открыть Swagger. Создать заявку, изменить описание и срок, перевести IN_PROGRESS → RESOLVED; показать history и closedAt.
3. (2 мин) Вызвать summary; объяснить Java ReportController → Kotlin ReportService → Java ReportSource/TicketStore; показать data class и nullable-среднее.
4. (1 мин) Попытаться открыть RESOLVED; сверить ответ и историю до/после. Запустить e2e forbiddenOperationLeavesDataUnchanged.
5. (3 мин) Показать реальный URL Actions и SHA образа, работающий readiness на учебном стенде, результаты smoke. Показать PR без deploy и блокировку publish при неуспешном verify.
6. (2 мин) Показать backup/restore в support_restore, переживание перезапуска, rollback.sh на предыдущий SHA и повторный smoke. Объяснить диагностику БД/health/Flyway из README.
7. (1 мин) Показать JaCoCo отдельно для Java бизнес-логики и Kotlin ReportService, регрессию D1 и ограничения.

Перед защитой заполнить evidence.md реальными ссылками и результатами. Без стенда и успешного pipeline обязательные условия зачёта ещё не выполнены.
