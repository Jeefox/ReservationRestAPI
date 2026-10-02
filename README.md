![CI](https://github.com/Jeefox/ReservationRestAPI/actions/workflows/ci.yml)

Postman-коллекция для регрессии: [`docs/postman-collection.json`](docs/postman-collection.json) — импортируй в Postman и гоняй все сценарии одной кнопкой (Collection Runner).

# Reservation System

REST API системы бронирования комнат со статусной машиной, event-driven уведомлениями и JWT-аутентификацией.

## 🛠 Стек

- **Java 17** (record'ы, switch expressions, text blocks)
- **Spring Boot 4** + Spring Data JPA + Hibernate 7 + **Spring Security 7**
- **JWT**: jjwt 0.12 (HMAC-SHA512), stateless-аутентификация
- **BCrypt** для паролей
- **PostgreSQL 16** + **Flyway** (миграции как код в git)
- **Docker Compose** (окружение одной командой)
- **SpringDoc OpenAPI 3** (Swagger UI + JSON-спека)
- **JUnit 5** + **Mockito** + **Testcontainers**

## 🏗 Архитектурные решения

- **Event-driven** через transactional outbox + Apache Kafka — бизнес-операция и запись события фиксируются в одной транзакции, отдельный publisher отправляет события в Kafka, consumer обрабатывает их идемпотентно
- **Статусная машина** — правила FSM инкапсулированы в enum, PATCH-эндпоинт `/reservations/{id}/status`
- **JWT вместо сессий** — stateless, сервер ничего не хранит между запросами
- **Закрытие IDOR** — `userId` убран из DTO, резолв текущего юзера из токена, ownership-проверки в сервисах
- **Двухуровневая авторизация** — URL-level (`hasRole("ADMIN")`) + object-level (`isOwner || isAdmin`)
- **Единый контракт ошибок** `ApiError` для всех 4xx/5xx
- **DTO-границы** на всех ресурсах (схемы входа и выхода изолированы от Entity)
- **Профили Spring**: `dev` (PostgreSQL + Swagger + сидер) и `prod` (PostgreSQL + Flyway, без Swagger)

## 🔐 Security

### Аутентификация

- **JWT-токены**: payload = `{sub, role, exp}`, подпись HMAC-SHA512
- **JwtAuthenticationFilter** (extends `OncePerRequestFilter`) читает `Authorization: Bearer`, валидирует подпись и срок, кладёт `Authentication` в `SecurityContextHolder`
- **UserDetailsService**-адаптер: мой `User` Entity → спринговый `UserDetails` с ролями `ROLE_USER` / `ROLE_ADMIN`
- **Production JWT secret** передаётся через переменную окружения `JWT_SECRET` и не хранится в репозитории
- При отсутствии `JWT_SECRET` production-профиль не запускается

### Ротация JWT secret

Production secret должен храниться во внешнем защищённом хранилище.

Для ротации:

1. Сгенерировать новый секрет:

```bash
openssl rand -base64 32
```
2. Заменить значение `JWT_SECRET` в production environment / secret store.
3. Перезапустить или переразвернуть приложение.
4. Проверить успешный запуск приложения.
5. Не использовать ранее опубликованный секрет повторно.

После смены секрета JWT, подписанные предыдущим ключом, перестают проходить проверку подписи.

### Пароли

- **BCrypt** (односторонний, с солью) — даже утечка БД не даст паролей
- `PasswordEncoder` — бин в `SecurityConfig`
- **Единое сообщение** "Неверный email или пароль" для обеих причин (email не найден / пароль не совпал) — не раскрываем существование аккаунтов

### Авторизация

- **URL-level**: `hasRole("ADMIN")` для POST/PUT/DELETE комнат и удаления пользователей
- **Object-level**: в сервисах проверяю `isOwner || isAdmin`, иначе `AccessDeniedException` → 403
- **APPROVED** — только для ADMIN (бизнес-правило: одобрять может только админ)

### Порядок правил в SecurityFilterChain

Специфичные правила **до** общих — иначе `/api/**` с `authenticated()` поглотит `hasRole("ADMIN")`. Классическая ошибка на собесах.

### Что не используем и почему

- **CSRF отключен**: для stateless REST API с токенами (а не cookie) неактуален
- **Сессии `STATELESS`**: сервер ничего не хранит, масштабируется горизонтально

## 📡 API endpoints

### Public (permitAll)

| Method | Path | Описание |
|--------|------|----------|
| POST | `/api/v1/auth/register` | Регистрация нового пользователя (USER) |
| POST | `/api/v1/auth/login` | Логин, возвращает JWT |
| GET | `/swagger-ui/**`, `/v3/api-docs/**` | OpenAPI-документация (только в dev) |

### Protected (authenticated)

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/v1/reservations` | Список броней (USER видит только свои, ADMIN — все) |
| GET | `/api/v1/reservations/{id}` | Бронь по ID |
| POST | `/api/v1/reservations` | Создать бронь (USER для себя) |
| PUT | `/api/v1/reservations/{id}` | Обновить бронь (owner или ADMIN) |
| DELETE | `/api/v1/reservations/{id}` | Удалить бронь (owner или ADMIN) |
| PATCH | `/api/v1/reservations/{id}/status` | Изменить статус (APPROVED — только ADMIN) |
| GET | `/api/v1/reservations/stats` | Статистика по комнатам |

### ADMIN only

| Method | Path |
|--------|------|
| POST / PUT / DELETE | `/api/v1/rooms/**` |
| DELETE | `/api/v1/users/**` |

## 🚀 Запуск

### Production (Docker + PostgreSQL + Flyway)

JWT secret должен быть передан через переменную окружения:

```bash
export JWT_SECRET='<production-secret>'

docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```
JWT_SECRET не должен храниться в Git или попадать в `application-prod.properties`.

При отсутствии `JWT_SECRET` приложение завершается с ошибкой при запуске.

### Development (PostgreSQL + Swagger + сидер)

```bash
mvn spring-boot:run
```
Профиль `dev` включается по умолчанию.

Особенности:

- **PostgreSQL** — используется та же СУБД, что и в production
- **Swagger UI**: http://localhost:8085/swagger-ui.html
- **Dev-сидер** при старте создаёт тестовые данные:
  - `ivan@email.com` / `password1` (USER)
  - `maria@email.com` / `password1` (USER)
  - `admin@admin.com` / `admin1` (ADMIN)
  - Две комнаты: `luxury` (2 места), `casual` (4 места)

### Типичный флоу в Postman

1. **Логин**:

```http
POST /api/v1/auth/login
```

Body:

```json
{
  "email": "ivan@email.com",
  "password": "password1"
}
```

Ответ:

```json
{
  "token": "eyJhbGciOi..."
}
```

2. **Запрос с токеном**:

```http
GET /api/v1/reservations
Authorization: Bearer eyJhbGciOi...
```

→ только свои брони

## 🧪 Тесты

```bash
mvn test
```

**Тесты** покрывают основные слои приложения:

| Уровень | Технология | Что проверяет |
|---------|------------|---------------|
| Unit | JUnit 5 + Mockito | `ReservationServiceTest` — статусная машина, конфликты дат, ownership, ролевые проверки |
| Security Unit | JUnit 5 | `JwtServiceTest` — отклонение JWT с неверной подписью |
| Controller | `@WebMvcTest` + `SecurityMockMvcRequestPostProcessors` | `ReservationControllerTest` — HTTP-контракт с security (201, 403, 404, 409) |
| Integration | `@SpringBootTest` + **Testcontainers** (PostgreSQL 16) + `@Sql` | `ReservationApiIntegrationTest` — JWT-флоу через всю систему |

### Что проверяет интеграционный тест

1. `@Sql` сидирует админа (обход "курицы-яйца": первого админа нельзя создать через API)
2. Админ логинится → создаёт комнату с Bearer-токеном
3. Регистрация обычного юзера через открытый `/auth/register`
4. Юзер логинится → создаёт бронь под своим токеном (без `userId` в body!)
5. Пересекающаяся бронь → 409
6. USER пытается создать комнату → **403** (role check на URL-level)
7. Анонимный GET → 4xx (без токена)

### Security-тест JWT

Проверяется, что токен, подписанный одним секретом, не принимается сервисом, использующим другой секрет:

```text
SECRET_A
   ↓
generateToken()
   ↓
JWT
   ↓
parseToken()
   ↓
SECRET_B
   ↓
SignatureException
```

Это защищает от принятия токенов, подписанных неизвестным ключом.

## 📂 Структура

```text
src/main/java/school/grevcev/reservation/
├── controller/          # REST-контроллеры + Swagger аннотации
├── service/             # Бизнес-логика + JwtService + AuthService
├── repository/          # Spring Data JPA + Specifications (фильтры)
├── model/               # JPA-сущности (User, Room, Reservation)
├── dto/                 # Request/Response records
├── config/              # SecurityConfig, OpenAPI
├── security/            # JwtAuthenticationFilter, UserDetailsService-адаптер
├── exception/           # Кастомные исключения + GlobalExceptionHandler
├── event/               # Spring Application Events + listeners
└── dbSeeder/            # Dev-сидер (profile=dev)

src/main/resources/
├── db/migration/        # Flyway migrations
├── application.properties
├── application-dev.properties
└── application-prod.properties

src/test/java/           # 43 теста (unit + security + controller + integration)
docs/                    # Postman-коллекция
```

## 📝 Контракт ошибок

Единый `ApiError` через `@RestControllerAdvice` для всех endpoint'ов:

```json
{
  "status": 409,
  "message": "Room already booked on these dates",
  "timestamp": "2026-08-29T11:00:00",
  "details": null
}
```

| Code | Когда |
|------|-------|
| 400 | Валидация DTO, malformed JSON, invalid types |
| 401 | Неверные credentials, отсутствует/невалидный токен |
| 403 | Не владелец ресурса, нет нужной роли |
| 404 | Сущность не найдена (user, room, reservation) |
| 409 | Email занят, даты пересекаются, невалидный переход статуса |
| 500 | Unexpected error (логируется с полным стектрейсом) |

## 📖 License

MIT