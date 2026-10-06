![CI](https://github.com/Jeefox/ReservationRestAPI/actions/workflows/ci.yml/badge.svg)

# Reservation System

REST API для бронирования комнат на Spring Boot. Приложение поддерживает JWT-аутентификацию, роли `USER`/`ADMIN`, проверку владельца брони, статусную машину и публикацию событий через transactional outbox и Kafka.

## Стек

- Java 17
- Spring Boot 4, Spring MVC, Spring Data JPA, Spring Security
- PostgreSQL 16 и Flyway
- Apache Kafka
- JWT (jjwt 0.12) и BCrypt
- SpringDoc OpenAPI
- JUnit 5, Mockito и Testcontainers

## Архитектура

- Пользователь регистрируется через `/api/v1/auth/register` и получает роль `USER`.
- JWT передаётся в заголовке `Authorization: Bearer <token>`.
- `USER` работает только со своими бронированиями, `ADMIN` имеет расширенные права.
- Создание брони и запись outbox-события выполняются в одной транзакции.
- Outbox publisher отправляет события в Kafka; consumer использует таблицу `processed_events` для идемпотентной обработки.
- Удаление брони является мягким: запись сохраняется со статусом `DELETED`.

## Запуск

### Development

Запусти PostgreSQL и Kafka:

```bash
docker compose up -d postgres kafka
```

Запусти приложение с явным dev-профилем:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Приложение доступно на `http://localhost:8085`.

Swagger UI: `http://localhost:8085/swagger-ui.html`.

Dev-профиль использует PostgreSQL на `localhost:5432`, базу `reservation_db`, пользователя `postgres` и пароль `postgres`. При пустой базе сидер создаёт:

| Email | Пароль | Роль |
|---|---|---|
| `ivan@email.com` | `password1` | `USER` |
| `maria@email.com` | `password1` | `USER` |
| `admin@admin.com` | `Admin123` | `ADMIN` |

Эти учётные данные предназначены только для локальной разработки.

### Production

Production требует внешний JWT-секрет:

```bash
export JWT_SECRET="<secret длиной не менее 256 бит>"
docker compose up -d postgres kafka
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

В production Swagger отключён. Значения подключения к БД и секреты необходимо задавать через конфигурацию окружения или secret manager; демонстрационные dev-учётные данные нельзя использовать в production.

## API

Все защищённые маршруты требуют JWT.

### Аутентификация

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Регистрация пользователя `USER` |
| `POST` | `/api/v1/auth/login` | Получение JWT |

Пример логина:

```http
POST http://localhost:8085/api/v1/auth/login
Content-Type: application/json

{
  "email": "ivan@email.com",
  "password": "password1"
}
```

### Комнаты

| Метод | Endpoint | Доступ |
|---|---|---|
| `GET` | `/api/v1/rooms` | Авторизованный пользователь |
| `GET` | `/api/v1/rooms/{id}` | Авторизованный пользователь |
| `POST` | `/api/v1/rooms` | `ADMIN` |
| `PUT` | `/api/v1/rooms/{id}` | `ADMIN` |
| `DELETE` | `/api/v1/rooms/{id}` | `ADMIN` |

### Бронирования

| Метод | Endpoint | Назначение |
|---|---|---|
| `POST` | `/api/v1/reservations` | Создание брони для текущего пользователя |
| `GET` | `/api/v1/reservations/{id}` | Получение брони владельцем или `ADMIN` |
| `GET` | `/api/v1/reservations` | Поиск с фильтрами, пагинацией и сортировкой |
| `PUT` | `/api/v1/reservations/{id}` | Изменение комнаты и дат владельцем или `ADMIN` |
| `DELETE` | `/api/v1/reservations/{id}` | Мягкое удаление владельцем или `ADMIN` |
| `PATCH` | `/api/v1/reservations/{id}/status` | Изменение статуса |
| `GET` | `/api/v1/reservations/stats` | Статистика по комнатам |

Создание брони:

```http
POST http://localhost:8085/api/v1/reservations
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "roomId": 1,
  "startDate": "2027-05-01",
  "endDate": "2027-05-05"
}
```

Статусы брони: `PENDING`, `APPROVED`, `CANCELLED`, `DELETED`. Переход в `APPROVED` доступен только администратору.

### Пользователи

| Метод | Endpoint | Доступ |
|---|---|---|
| `GET` | `/api/v1/users` | Авторизованный пользователь |
| `GET` | `/api/v1/users/{id}` | Авторизованный пользователь |
| `POST` | `/api/v1/users` | Устаревший маршрут; для создания аккаунта используйте `/auth/register` |
| `PUT` | `/api/v1/users/{id}` | Владелец профиля или `ADMIN` |
| `DELETE` | `/api/v1/users/{id}` | `ADMIN` |

## Тесты

```bash
./mvnw test
```

Unit- и controller-тесты запускаются локально. Интеграционные тесты используют Testcontainers и требуют работающий Docker daemon; они поднимают PostgreSQL и Kafka автоматически.

## Postman

Актуальная коллекция находится в [`docs/postman-collection.json`](docs/postman-collection.json). Все запросы к приложению используют порт `8085`, получают JWT через `/api/v1/auth/login` и передают его в `Authorization`.

## Структура

```text
src/main/java/grevcev/
├── auth/          # JWT, регистрация и безопасность
├── reservation/   # бронирования, статусы и поиск
├── room/          # комнаты
├── user/          # пользователи
├── outbox/        # transactional outbox
├── kafka/         # producer, consumer и обработчики
├── notification/  # уведомления
├── exception/     # единый контракт ошибок
└── db/            # dev-сидер

src/main/resources/db/migration/  # Flyway-миграции
src/test/                          # unit, MVC и интеграционные тесты
```

## Лицензия

MIT
