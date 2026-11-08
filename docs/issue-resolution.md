# Исправления открытых issues

Проверены задачи репозитория Jeefox/ReservationRestAPI. Ниже — соответствие изменений задачам;
этот документ не меняет состояние issues на GitHub.

| Issues | Решение | Проверка |
|---|---|---|
| #4, #6 | Список пользователей — ADMIN, профиль — владелец/ADMIN; ApiError для 401/403, БД при JWT — 503 с логированием | ReservationInvariantIntegrationTest, JwtAuthenticationFilterTest |
| #7 | Единые существующие ограничения паролей; dev-сидер использует PasswordEncoder вместо ошибочного фиксированного хэша; rate limit описан для gateway | AuthValidationIntegrationTest, DevProfileIntegrationTest |
| #8, #11 | endDate > startDate; дата начала проверяется в create/PUT DTO; сущность допускает исторические даты | ReservationInvariantIntegrationTest; V8 DB CHECK |
| #9 | Soft delete сохраняет FK и журнал независимо от порядка удаления/обработки CREATED | ReservationApiIntegrationTest, ReservationInvariantIntegrationTest |
| #10 | @Version предотвращает запоздавшее APPROVED и потерю PUT; конфликт → 409 | ReservationConcurrencyIntegrationTest: две детерминированные гонки |
| #12 | Изменение параметров APPROVED → PENDING; CANCELLED/DELETED и даты до сегодня не редактируются | ReservationInvariantIntegrationTest |
| #13, #28 | Независимые границы, from >= to → 400; findAll(Specification, Pageable), относительные даты тестов | Реальные PostgreSQL-фильтры, сортировка и пагинация |
| #14 | Статистика считает PENDING/APPROVED, использует строгие границы; комнаты с нулём отсутствуют | ReservationInvariantIntegrationTest |
| #15 | capacity обязательна и положительна в PUT, DB CHECK | ReservationInvariantIntegrationTest |
| #16 | Вместимость справочная; бронируется комната целиком | Согласовано владельцем, README |
| #17 | EXISTS вместо загрузки списка конфликтов, частичный индекс активных броней | QueryPlanIntegrationTest: EXPLAIN ANALYZE на 100 000 строк |
| #18 | Внешний контракт только CREATED; заглушки APPROVED/CANCELLED удалены; статусы остаются локальными Spring-событиями | Согласовано владельцем; Kafka/rollback-тесты, operations.md |
| #19 | ErrorHandlingDeserializer, ограниченные retries, подтверждаемая публикация DLT с причиной | KafkaRecoveryIntegrationTest: malformed JSON, отсутствующая бронь, временный сбой, следующее сообщение |
| #20 | notification — внутренний журнал RECORDED; миграция старых статусов, eventId уникален | Согласовано владельцем; NotificationIntegrationTest и повтор CREATED |
| #21 | Настраиваемые batch/lease/retries, attempts/last_error/FAILED, защита от старого callback, SQL-метрики | OutboxRetryIntegrationTest |
| #22 | Topic/group/concurrency/partitions/replicas — конфигурация; отдельные имена dev/prod; версии Kafka/UI закреплены | DevProfileIntegrationTest и изолированная Compose smoke-проверка |
| #23 | Индексы очереди/дедупликации; сроки хранения, безопасные batch DELETE, мониторинг и восстановление | V10, QueryPlanIntegrationTest, operations.md |
| #24 | POST /users и небезопасный save удалены; создание аккаунта — /auth/register | ReservationInvariantIntegrationTest: POST → 405 |
| #25, #32 | Явный dev-профиль, PostgreSQL/Kafka; пустая БД, Swagger и dev-аккаунты проверяются по HTTP | DevProfileIntegrationTest, ConfigurationContractTest; README |
| #26 | Prod требует внешние настройки; описаны первый ADMIN и восстановление аккаунтов до V2 | ConfigurationContractTest, operations.md |
| #27 | Postman: 8085, JWT, правильные DTO/PATCH, динамические даты, отдельная комната | DevProfileIntegrationTest исполняет HTTP-запросы коллекции по порядку; README |

Решения требуют новых Flyway-миграций V8–V10. Для production учтите новые обязательные переменные
и перенос Kafka topic/group, а для исторических данных — процедуру VALIDATE CONSTRAINT в operations.md.

## Выполненная проверка

- `./mvnw clean verify`: 86 тестов, включая PostgreSQL/Kafka Testcontainers и сборку JAR.
- Запуск `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` без внешнего JWT_SECRET:
  для изоляции переопределены только порты/адреса зависимостей; Swagger и API дали 200.
- Все 10 запросов коллекции и её JavaScript-проверки исполнены против этого HTTP-сервера.
- Изолированный Compose: apache/kafka:3.8.0 и Kafka UI v0.7.2; broker online. Временные контейнеры удалены.
- `EXPLAIN ANALYZE`: 100 000 броней и 100 000 обработанных outbox-событий плюс готовая очередь;
  PostgreSQL использует частичные индексы. Выбор конкретного подходящего индекса зависит от статистики.
- `git diff --check`.
