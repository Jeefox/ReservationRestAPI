# Production и эксплуатация

## Конфигурация

Compose в корне — только локальная среда: тестовые пароли, открытые порты и один Kafka broker.
Для production используйте управляемые PostgreSQL/Kafka либо отдельную инфраструктурную конфигурацию.
Обязательные переменные:

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL=jdbc:postgresql://<host>:5432/<database>`
- `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET` — случайный секрет минимум 32 байта, из secret manager
- `KAFKA_BOOTSTRAP_SERVERS`

В prod нет резервного dev-секрета или тестового пароля БД. Swagger отключён.
В production явно задайте `KAFKA_REPLICAS` по числу broker.
Для TLS/SASL Kafka задавайте стандартные `spring.kafka.properties.*` и настройки producer/consumer через конфигурацию Spring.

## Первый администратор

1. Зарегистрируйте личный аккаунт через `POST /api/v1/auth/register` по HTTPS. Он получает роль USER.
2. Проверенный оператор БД повышает только этот аккаунт через закрытое подключение. В psql:

   ```sql
   \prompt 'Email проверенного администратора: ' admin_email
   BEGIN;
   UPDATE users SET role = 'ADMIN'
   WHERE email = :'admin_email' AND password IS NOT NULL
   RETURNING id, email, role;
   -- Проверьте, что возвращена ровно одна нужная запись.
   COMMIT;
   ```

3. Выполните новый login и проверьте административный маршрут. Сохраните аудит повышения роли.

Публичного endpoint для назначения ADMIN нет. Dev-сидер в production не работает.
Если UPDATE вернул неожиданную запись или количество, выполните ROLLBACK вместо COMMIT.

## Пользователи до миграции V2

V2 добавляла nullable password: прежние аккаунты могут не иметь пароля. Они не должны получать общий пароль.
Сначала определите их закрытым запросом `SELECT id, email FROM users WHERE password IS NULL`.
Свяжите аккаунт с подтверждённым владельцем и проведите индивидуальное восстановление доступа.
Публичный password reset сейчас не реализован: оператор генерирует уникальный временный пароль,
хэширует тем же `LengthSafePasswordEncoder`, что использует приложение, и обновляет только проверенный ID.
Передача временного пароля — отдельным защищённым каналом. Пользователь должен заменить его через
организационную процедуру; публичного API смены пароля пока нет. До этого не включайте старый аккаунт
в production. Недопустимо хэшировать длинные пароли обычным BCrypt вместо используемого encoder.

## Kafka

| Параметр | По умолчанию |
|---|---|
| `KAFKA_RESERVATIONS_TOPIC` | `reservations-dev` / `reservations-prod` |
| `KAFKA_RESERVATIONS_GROUP` | `reservation-service-dev` / `reservation-service-prod` |
| `KAFKA_PARTITIONS` | 3 |
| `KAFKA_REPLICAS` | 1 для локального запуска; в prod задайте по числу broker |
| `KAFKA_CONCURRENCY` | 2 |
| `KAFKA_RETRY_DELAY_MS` | 1000 |
| `KAFKA_RETRY_ATTEMPTS` | 2 повтора после первой попытки |

Единственное внешнее событие — `RESERVATION_CREATED`: outbox записывается атомарно с бронью.
APPROVED/CANCELLED — локальные Spring-события для логирования после коммита. Внешних обработчиков-заглушек нет.
При переходе со старого topic/group сначала остановите старые экземпляры и дренируйте очередь,
затем явно задайте старое имя на время миграции либо перенесите offsets управляемой процедурой.
Не меняйте group без решения о повторном чтении истории.

Ошибки JSON и необрабатываемые события сохраняются в `<topic>.DLT` вместе с причиной в headers.
Исходный некорректный JSON сохраняется как bytes; ошибки обработчика — как JSON envelope.
Offset исходного сообщения не считается восстановленным, пока публикация в DLT не подтверждена.
После устранения причины оператор может переопубликовать сообщение, сохранив eventId и ключ комнаты.
До повтора проверьте `processed_events` и журнал `notification`; не заменяйте eventId для обхода дедупликации.
DLT требует мониторинга и срока хранения не меньше согласованного срока расследования (90 дней по умолчанию).
Порядок обработки после DLT не гарантирован для пропущенного события; журнал создания не зависит от следующих статусов.

`notification` — внутренний журнал фактов создания брони со статусом RECORDED, а не очередь доставки email.
Для каждого eventId допускается одна запись. Мягкое удаление брони сохраняет и бронь, и запись журнала;
CREATED, обработанное после удаления, также записывается. Историческая колонка sent_at сохранена в БД.

## Outbox, повторы и метрики

Настройки: `OUTBOX_BATCH_SIZE=10`, `OUTBOX_MAX_ATTEMPTS=5`, `OUTBOX_LEASE=PT10M`,
`OUTBOX_RETRY_DELAY=PT10S`, `OUTBOX_PUBLISH_DELAY_MS=5000`.
После исчерпания попыток событие получает FAILED. Последующие события с тем же partition_key ждут,
чтобы очередь не обходила проблемное событие. Публикация допускает дубликаты; consumer идемпотентен.
Поздний callback предыдущей попытки не завершает новую попытку.

Проверяйте SQL-метрики (доступны для сборщика мониторинга с read-only доступом):

```sql
SELECT * FROM outbox_queue_metrics;
SELECT id, attempts, last_error FROM outbox_events WHERE status = 'FAILED';
```

Поля: backlog, failed, failed_attempts (попытки у событий с последней ошибкой), oldest_age_seconds.
Алерты: failed > 0; oldest_age_seconds > 600; устойчивый рост backlog.

После исправления причины ручной повтор конкретного события:

```sql
-- Подставьте проверенный UUID через параметр вашего SQL-клиента.
UPDATE outbox_events SET status = 'NEW', attempts = 0, next_attempt_at = NULL,
    processing_started_at = NULL, last_error = NULL
WHERE id = :event_id AND status = 'FAILED';
```

## Хранение и очистка

Политика: PROCESSED outbox — 30 дней; processed_events — 90 дней.
NEW/PROCESSING/FAILED автоматически не удаляются. Журнал notification хранится по отдельной политике продукта.
Удаление processed_events допустимо только при максимальном возрасте повторного чтения менее 90 дней:
учитывайте retention Kafka, DLT, backups и ручное восстановление offsets. Если этот предел не обеспечен,
не очищайте processed_events. Для восстановления более старых событий сначала восстановите дедупликацию из архива.

Архивируйте удаляемые записи, очищайте небольшими партиями вне пика нагрузки:

```sql
DELETE FROM outbox_events WHERE id IN (
    SELECT id FROM outbox_events WHERE status = 'PROCESSED'
      AND processed_at < localtimestamp - interval '30 days'
    ORDER BY processed_at LIMIT 1000
);
DELETE FROM processed_events WHERE event_id IN (
    SELECT event_id FROM processed_events
    WHERE processed_at < localtimestamp - interval '90 days'
    ORDER BY processed_at LIMIT 1000
);
```

Контролируйте размеры таблиц и индексов через `pg_total_relation_size`, количество строк и autovacuum.
V8/V10 добавляют частичные индексы под активные брони, готовую очередь и незавершённые ключи.
Ограничения дат и capacity добавлены NOT VALID, чтобы не переписывать исторические данные:
найдите и исправьте строки с end_date <= start_date / capacity <= 0, затем выполните
`ALTER TABLE reservations VALIDATE CONSTRAINT reservation_dates_order` и
`ALTER TABLE rooms VALIDATE CONSTRAINT room_positive_capacity`.

## Публичная аутентификация

Установите rate limit на `/api/v1/auth/login` и `/register` на reverse proxy / API gateway,
с ограничением по IP и мониторингом ошибок. Встроенного распределённого rate limiter пока нет;
локальный счётчик в одном экземпляре не защитит кластер.
