# OrbitaMarket

Учебная платформа БЮРО 1440 для заказа спутниковых продуктов за геокредиты. Проект реализует обязательное ядро итогового задания и расширяет его личным кабинетом, JWT-аутентификацией, уведомлениями, интерактивной картой и Rust-сервисом геообработки.

## Что реализовано

- **Payments Service (Java):** один счёт на пользователя, пополнение, баланс, конкурентно-безопасное и идемпотентное списание.
- **Orders Service (Java):** `ARCHIVE`, `TASKING`, `MONITORING`, валидация payload, жизненный цикл `CREATED → PAYMENT_PENDING → PAID/PAYMENT_FAILED`, `REJECTED` и `failure_reason`.
- **API Gateway (Java):** единая точка входа, маршрутизация и передача `X-User-Id`; при JWT заголовок формируется из `sub` токена; единый Swagger UI агрегирует OpenAPI всех Java-сервисов.
- **Auth Service (Java):** регистрация, вход, BCrypt, JWT HS384 и редактирование профиля.
- **Notifications Service (Java):** идемпотентные уведомления об оплате, история, read/read-all и SSE для колокольчика.
- **Geo Pricing Service (Rust):** проверка GeoJSON, самопересечений и площади, тариф, bbox, HTTP/gRPC и получение спутникового PNG.
- **Frontend (React + TypeScript + Leaflet):** личный кабинет, кошелёк, заказы, обычная карта для выбора AOI, отображение точек/полигона, Rust-цена и журнал продуктов.
- **PostgreSQL + Kafka:** изолированные схемы сервисов, transactional outbox/inbox и choreography saga.
- **Redis:** read-through кэш баланса и заказов с TTL, AOF и инвалидацией на всех write/Kafka-переходах.
- **Liquibase:** версионируемые таблицы, ограничения и индексы; Hibernate работает в режиме `validate`.

## Полный технологический стек

| Слой | Технологии и библиотеки | Для чего используются |
|---|---|---|
| Язык backend | Java 21 | Payments, Orders, Gateway, Auth, Notifications и Kafka contracts |
| Java framework | Spring Boot 3.4.5 | конфигурация, DI, REST, lifecycle и production packaging |
| HTTP/API | Spring Web, Jakarta Validation, Jackson | REST endpoints, DTO/JSON и единый формат ошибок |
| API documentation | Springdoc OpenAPI 2.8.8, Swagger UI | интерактивная документация четырёх Java REST API через Gateway |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL JDBC | агрегаты сервисов и транзакции |
| Messaging | Spring Kafka, Apache Kafka 3.9 | асинхронная saga оплаты, outbox/inbox |
| Gateway | Spring Cloud Gateway 2024.0.1, Reactor | reactive routing, StripPrefix и identity filter |
| Security | Spring Security Crypto, BCrypt, JJWT 0.12.6 | пароли, JWT HS384, trusted `X-User-Id` |
| Cache | Spring Cache, Spring Data Redis, Redis 7.4 | TTL-кэш баланса/заказов, AOF и инвалидация |
| Миграции | Liquibase | версионирование таблиц, constraints и индексов |
| Основная БД | PostgreSQL 16 | изолированные схемы и конкурентно-безопасный баланс |
| Geo backend | Rust 2024 edition | вычислительно сложная геометрия, цена, tiles и snapshot |
| Rust HTTP/async | Axum 0.8, Tokio, Tower HTTP | быстрый HTTP API, CORS, tracing и tile proxy |
| Rust gRPC | Tonic, Prost, Protocol Buffers | бинарный контракт Geo Pricing |
| Rust data/network | GeoJSON, Serde, Reqwest + rustls, UUID | GeoJSON, JSON, imagery HTTP и quote id |
| Frontend | React, TypeScript, Vite | SPA операционного центра |
| GIS frontend | Leaflet, OpenStreetMap | pan/zoom и интерактивное выделение AOI |
| UI | Lucide React, CSS Grid/Flex, responsive CSS | иконки и визуальная система БЮРО 1440 |
| Reverse proxy | Nginx 1.27 | SPA hosting, same-origin API/SSE proxy, Docker DNS |
| Контейнеризация | Docker, Docker Compose | сборка и запуск всего контура одной командой |
| Java tests | JUnit 5, Mockito, Maven Surefire, Rest Assured, Awaitility | unit и black-box E2E |
| Test reports | Allure adapter | результаты системного прогона |
| Browser tests | Playwright Core + Microsoft Edge | реальные tiles, AOI и доступность заказа |
| Quality | Spotless/Google Java Format, Prettier, Cargo fmt, Clippy | читаемость и статические проверки |
| Security checks | Gitleaks, Semgrep | финальные JSON-отчёты и документированный TP/FP/risk triage |
| Architecture/docs | C4-PlantUML, Markdown, SQL | C1/C2, поток оплаты, compliance и аналитика |

## Быстрый запуск

Требуются Docker Desktop и Docker Compose.

```bash
docker compose up --build -d
```

- веб-приложение: http://localhost:3000;
- API Gateway: http://localhost:8080;
- PostgreSQL для локальной диагностики: `localhost:5433`;
- Kafka для системных тестов: `localhost:9092`.
- Redis для диагностики: `localhost:6379`.
- Swagger UI для всех Java REST API: http://localhost:8080/swagger-ui.html.

В Swagger UI сервис выбирается в верхнем выпадающем списке. Для защищённых запросов кнопка **Authorize** принимает JWT (`bearerAuth`) либо учебный заголовок `X-User-Id`; кнопка **Try it out** отправляет запрос через API Gateway.

Первый запуск может занять несколько минут из-за загрузки Maven, npm и Cargo-зависимостей. Состояние:

```bash
docker compose ps
docker compose logs -f api-gateway orders-service payments-service
```

Остановка:

```bash
docker compose down
```

## Пользовательский сценарий

1. Зарегистрироваться или войти.
2. Создать кошелёк и пополнить баланс.
3. Выбрать продукт: архив, разовая съёмка или мониторинг.
4. На **обычной карте** отметить минимум три точки и нажать «Готово».
5. Выбрать пространственное разрешение; Rust проверит AOI и рассчитает площадь, bbox и серверную цену.
6. После отправки заказ сразу получает `CREATED`; outbox асинхронно передаёт команду оплаты.
7. Payments списывает геокредиты не более одного раза по `order_id`.
8. После `PAID` формируется спутниковый PNG World Imagery: пропорции Web Mercator сохранены, изображение маскируется по исходному полигону AOI. Камера оплаченного заказа повторяет формирование, если автоматический результат ещё не сохранён.

Карта конструктора намеренно обычная. Спутниковое изображение появляется только как результат оплаченного заказа и является учебной имитацией продукта на основе существующего World Imagery, а не заявлением о реальном управлении космическим аппаратом.

## Семантика продуктов

| Тип | Учебная модель | Payload | Базовая цена / тариф площади |
|---|---|---|---:|
| `ARCHIVE` | существующий архивный снимок | `aoi`, `capture_date`, `sensor_type` | `20 GC + 0.8 GC/км²` |
| `TASKING` | новая одноразовая съёмка в будущем окне | `aoi`, `time_window.from/to`, `sensor_type` | `90 GC + 2.5 GC/км²` |
| `MONITORING` | подписка на повторную съёмку | `aoi`, `cadence`, `duration_days` | `180 GC + 4.0 GC/км²` |

Итоговая формула Rust: `(базовая цена + площадь × тариф) × коэффициент разрешения`. Доступны 0.3, 0.5, 0.8, 1.5 и 3 м/пиксель: более детальный продукт дороже. Zoom карты на цену не влияет, поскольку меняет только отображение, а не геометрию AOI.

Базовое ТЗ разрешает cadence `DAILY` или `WEEKLY`. Текущий веб-сценарий создаёт `WEEKLY` на 30 дней; фактический спутниковый планировщик и почасовая/поминутная доставка находятся вне учебной модели.

## REST API через Gateway

В учебных API можно передать `X-User-Id` вручную. В веб-приложении используется JWT, и Gateway перезаписывает `X-User-Id` значением `sub`, не доверяя клиентскому заголовку при наличии токена.

### Payments

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/payments/api/v1/payments/accounts` | создать счёт идемпотентно |
| `POST` | `/payments/api/v1/payments/accounts/top-up` | пополнить на `amount > 0` |
| `GET` | `/payments/api/v1/payments/accounts/balance` | получить баланс |

### Orders

| Метод | Путь | Назначение |
|---|---|---|
| `POST` | `/orders/api/v1/orders/orders` | создать заказ и инициировать оплату |
| `GET` | `/orders/api/v1/orders/orders` | список заказов пользователя |
| `GET` | `/orders/api/v1/orders/orders/{order_id}` | детали и статус заказа |

### Auth, notifications и geo

- `POST /auth/api/v1/auth/register`, `POST /auth/api/v1/auth/login`;
- `GET/PUT /auth/api/v1/profile`;
- `GET /notifications/api/v1/notifications`;
- `PATCH /notifications/api/v1/notifications/{id}/read`;
- `PATCH /notifications/api/v1/notifications/read-all`;
- `GET /notifications/api/v1/notifications/stream`;
- `POST /geo/api/v1/geo/analyze`;
- `POST /geo/api/v1/geo/snapshot`;
- `GET /geo/api/v1/geo/tiles/{street|satellite}/{z}/{y}/{x}`.

Ошибки обязательных сервисов имеют единый вид:

```json
{
  "error_code": "INVALID_AMOUNT",
  "message": "Amount must be greater than zero",
  "timestamp": "2026-07-12T12:00:00Z"
}
```

## Асинхронная оплата

| Kafka topic | Направление | События |
|---|---|---|
| `orders.payment-requests` | Orders → Payments | `OrderPaymentRequested` |
| `payments.results` | Payments → Orders и Notifications | `OrderPaymentCompleted`, `OrderPaymentFailed` |

Orders записывает заказ и outbox в одной транзакции. Publisher отправляет событие и после broker ACK переводит заказ в `PAYMENT_PENDING`. Payments атомарно выполняет условный SQL `balance >= amount`, фиксирует inbox, payment с уникальным `order_id` и outbox результата. Orders применяет результат через собственный inbox. Повторная доставка не создаёт второго финансового эффекта — effectively exactly-once списание поверх at-least-once доставки Kafka.

Подробный поток: [docs/payment-flow.md](docs/payment-flow.md).

## Проверка качества

```bash
mvn clean test
mvn spotless:check
cd geo-pricing-service && cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test
cd frontend && npm ci && npm run build
docker compose up --build -d
mvn -f autotests/pom.xml test
cd frontend && npm run test:map
```

`autotests` — независимый Maven-проект без зависимостей на внутренние классы сервисов. Для выполнения требования курса его каталог нужно опубликовать отдельным публичным Git-репозиторием. Allure CLI устанавливается отдельно; результаты тестов создаются в `autotests/target/allure-results`.

## Документация

- [PROJECT.md](PROJECT.md) — цель, стейкхолдеры и roadmap;
- [docs/requirements-compliance.md](docs/requirements-compliance.md) — выполнение требований итогового проекта и дополнительные инженерные возможности;
- [docs/checklist.md](docs/checklist.md) — приёмочные сценарии;
- [docs/payment-flow.md](docs/payment-flow.md) — поток оплаты и гарантии;
- [docs/c4-context.puml](docs/c4-context.puml), [docs/c4-containers.puml](docs/c4-containers.puml) — исходники C1/C2;
- [docs/analytics.sql](docs/analytics.sql) — «кто и сколько купил»;
- [docs/security-triage.md](docs/security-triage.md) — security triage;
- [docs/verification.md](docs/verification.md) — результаты последнего полного прогона;
- [docs/final-audit.md](docs/final-audit.md) — честная построчная приёмка по требованиям LMS и оставшиеся внешние артефакты;
- [docs/gitleaks-report.json](docs/gitleaks-report.json), [docs/semgrep-report.json](docs/semgrep-report.json) — реальные результаты финального инструментального сканирования.

PDF C4, презентация и публикация `autotests` отдельным репозиторием оформляются отдельно от программного репозитория.

## Production-ограничения

Это учебный MVP. Перед production необходимы: собственный `JWT_SECRET`, TLS, secrets manager, review/backup-процесс для Liquibase, отдельные роли/БД, Kafka ACL/TLS, rate limiting, private object storage вместо браузерного IndexedDB, постоянный fulfillment worker и лицензированный imagery provider с подходящими условиями хранения/распространения.
