![CI](https://github.com/Jeefox/ReservationSpring/actions/workflows/ci.yml/badge.svg)

Postman-коллекция для регрессии: [`docs/postman-collection.json`](docs/postman-collection.json) — импортируй в Postman и гоняй все сценарии одной кнопкой (Collection Runner).

# Reservation System

REST API системы бронирования комнат со статусной машиной, event-driven уведомлениями, JWT-аутентификацией и infrastructure-as-code.

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
- **Event-driven** через `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` — сервис не знает о уведомлениях, слабая связанность
- **Статусная машина** — правила FSM инкапсулированы в enum, PATCH-эндпоинт `/reservations/{id}/status`
- **JWT вместо сессий** — stateless, сервер ничего не хранит между запросами
- **Закрытие IDOR** — `userId` убран из DTO, резолв текущего юзера из токена, ownership-проверки в сервисах
- **Двухуровневая авторизация** — URL-level (`hasRole("ADMIN")`) + object-level (`isOwner || isAdmin`)
- **Единый контракт ошибок** `ApiError` для всех 4xx/5xx
- **DTO-границы** на всех ресурсах (схемы входа и выхода изолированы от Entity)
- **Профили Spring**: `dev` (H2 + Swagger + сидер) и `prod` (PostgreSQL + Flyway, без Swagger)

## 🔐 Security

### Аутентификация
- **JWT-токены**: payload = `{sub, role, exp}`, подпись HMAC-SHA512 (секрет ≥ 512 бит в `app.jwt.secret`)
- **JwtAuthenticationFilter** (extends `OncePerRequestFilter`) читает `Authorization: Bearer`, валидирует подпись и срок, кладёт `Authentication` в `SecurityContextHolder`
- **UserDetailsService**-адаптер: мой `User` Entity → спринговый `UserDetails` с ролями `ROLE_USER` / `ROLE_ADMIN`
- **Секрет в properties**, не в коде — легко ротировать без передеплоя

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
```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Development (H2 + Swagger + сидер)
```bash
mvn spring-boot:run
```

Профиль `dev` включается по умолчанию. Особенности:
- **H2 in-memory** — не нужен Docker для быстрой разработки
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Dev-сидер** при старте создаёт тестовые данные:
  - `ivan@email.com` / `password1` (USER)
  - `maria@email.com` / `password1` (USER)
  - `admin@admin.com` / `admin1` (ADMIN)
  - Две комнаты: `luxury` (2 места), `casual` (4 места)

### Типичный флоу в Postman

1. **Логин**:
```
POST /api/v1/auth/login
Body: {"email":"ivan@email.com", "password":"password1"}
→ {"token":"eyJhbGciOi..."}
```

2. **Запрос с токеном**:
```
GET /api/v1/reservations
Authorization: Bearer eyJhbGciOi...
→ только свои брони
```

## 🧪 Тесты

```bash
mvn test
```

**39 тестов** покрывают все слои:

| Уровень | Технология | Что проверяет |
|---------|------------|---------------|
| Unit | JUnit 5 + Mockito | `ReservationServiceTest` — статусная машина, конфликты дат, ownership, ролевые проверки |
| Controller | `@WebMvcTest` + `SecurityMockMvcRequestPostProcessors` | `ReservationControllerTest` — HTTP-контракт с security (201, 403, 404, 409) |
| Integration | `@SpringBootTest` + **Testcontainers** (postgres:16) + `@Sql` | `ReservationApiIntegrationTest` — JWT-флоу через всю систему |

### Что проверяет интеграционный тест
1. `@Sql` сидирует админа (обход "курицы-яйца": первого админа нельзя создать через API)
2. Админ логинится → создаёт комнату с Bearer-токеном
3. Регистрация обычного юзера через открытый `/auth/register`
4. Юзер логинится → создаёт бронь под своим токеном (без userId в body!)
5. Пересекающаяся бронь → 409
6. USER пытается создать комнату → **403** (role check на URL-level)
7. Анонимный GET → 4xx (без токена)

## 📂 Структура

```
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
├── db/migration/        # Flyway: V1__init.sql, V2__add_security.sql
├── application.properties
├── application-dev.properties
└── application-prod.properties

src/test/java/           # 39 тестов (unit + controller + integration)
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
