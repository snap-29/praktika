# Контракт REST API

База `/api`, JSON UTF-8. Полный машинный OpenAPI генерируется работающим приложением на `/v3/api-docs`, Swagger — `/swagger-ui/index.html`. Экспорт проверенного контракта: `docs/openapi.json`. Готовая коллекция: `demo/requests.http`.

| Метод | Путь | Назначение / успех |
|---|---|---|
| POST | /tickets | Создать, 201 и Location |
| GET | /tickets/{UUID} | Получить, 200 |
| PUT | /tickets/{UUID} | Заменить description/priority/dueAt, 200 |
| PATCH | /tickets/{UUID}/status | Переход, 200 |
| GET | /tickets | Фильтры и пагинация, 200 |
| GET | /categories | Справочник, 200 |
| GET | /tickets/{UUID}/history | История по ID записи, 200 |
| GET | /reports/summary?from=...&to=... | Kotlin-статистика, 200 |

POST `/tickets`:
```json
{"title":"Принтер","description":"Не печатает","categoryId":1,"priority":"HIGH","dueAt":"2026-12-01T12:00:00Z"}
```
Ответ 201 (времена/UUID — пример):
```json
{"id":"11111111-1111-1111-1111-111111111111","title":"Принтер","description":"Не печатает","categoryId":1,"priority":"HIGH","status":"NEW","createdAt":"2026-10-20T12:00:00Z","dueAt":"2026-12-01T12:00:00Z","closedAt":null}
```
GET по ID возвращает такой же объект. PUT принимает `{"description":"Уточнено","priority":"LOW","dueAt":null}` и возвращает обновлённую заявку. PATCH принимает `{"status":"IN_PROGRESS"}`, возвращает заявку с новым статусом. Для RESOLVED/CANCELLED closedAt заполняется временем сервера. Попытка вновь открыть терминальную заявку — 409.

GET `/tickets?status=NEW&priority=HIGH&categoryId=1&overdue=true&page=0&size=20`: все фильтры соединяются AND; каждый необязателен. `overdue=false` выбирает всё, что не является открытой просроченной заявкой. Ответ `{"content":[...],"totalElements":1,"page":0,"size":20}`. Пустая/выходящая за конец страница содержит пустой content, корректный totalElements. GET categories: `[{"id":1,"name":"Hardware"},{"id":2,"name":"Software"},{"id":3,"name":"Network"}]`.

GET history: `[{"id":1,"fromStatus":null,"toStatus":"NEW","changedAt":"2026-10-20T12:00:00Z"}]`. Несуществующая заявка для чтения, изменения и истории — 404.

GET `/reports/summary?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z`:
```json
{"from":"2026-10-01T00:00:00Z","to":"2026-11-01T00:00:00Z","createdCount":3,"byStatus":{"NEW":1,"IN_PROGRESS":0,"RESOLVED":1,"CANCELLED":1},"byPriority":{"LOW":1,"MEDIUM":0,"HIGH":2},"overdueOpenCount":1,"averageResolutionSeconds":3600.0}
```
При пустой выборке createdCount и счётчики равны 0, averageResolutionSeconds=null. Обязательные from и to — ISO-8601 UTC, from < to. Период полуоткрытый. Просрочка считается относительно текущего Clock, не to.

Ошибки:
```json
{"timestamp":"2026-10-20T12:00:00Z","status":409,"message":"Forbidden status transition","path":"/api/tickets/11111111-1111-1111-1111-111111111111/status"}
```
400 — обязательные поля, неизвестный enum, неверный UUID/Instant/период/пагинация/категория, лишнее JSON-поле. 404 — отсутствующая заявка. 409 — запрещённый переход или изменение терминальной заявки. 500 — внутренняя ошибка с деталями только в логах. Возврат 409 не меняет статус, поля, closedAt и историю.
