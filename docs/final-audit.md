# Итоговый аудит OrbitaMarket

Дата проверки: 13.07.2026. Источник требований: полное техническое задание LMS «Индустриальная разработка ПО: Итоговый проект по программе», включая таблицы, схемы и критерии оценки.

## Итоговый вердикт

Программное ядро технического задания реализовано и подтверждено автоматическими проверками. Payments, Orders, API Gateway, Kafka flow, Outbox/Inbox, идемпотентность по `order_id`, конкурентно-безопасный баланс, обязательные REST API, SQL и пять приёмочных сценариев работают.

Полный комплект сдачи нельзя называть завершённым до добавления двух внешних артефактов: C4 PDF и публичного URL отдельного репозитория автотестов. Финальные Gitleaks/Semgrep JSON уже сформированы и разобраны. Оставшиеся пункты не являются дефектами runtime-кода, но входят в критерии LMS.

## Матрица обязательных требований

| Раздел | Результат проверки | Доказательство | Статус |
|---|---|---|---|
| Планирование | цель, 3 стейкхолдера, roadmap с датами до 12.07.2026 | `PROJECT.md` | выполнено |
| Границы сервисов | Payments и Orders независимы; Gateway не хранит бизнес-состояние | модули Maven, C2, Compose | выполнено |
| Изоляция данных | отдельные схемы PostgreSQL, межсервисных таблиц/FK нет | Liquibase changelog | выполнено |
| Payments API | account, top-up, balance и обязательные ошибки | E2E + OpenAPI | выполнено |
| Orders API | create/list/details, 3 типа payload, статусы и `failure_reason` | E2E + unit | выполнено |
| Асинхронная оплата | POST возвращает `CREATED`, дальнейшая оплата проходит через Kafka | E2E happy path | выполнено |
| Надёжная доставка | transactional outbox/inbox в Orders и Payments | транзакционный код + БД constraints | выполнено |
| Effectively exactly-once | повтор команды с новым `event_id`, но тем же `order_id`, не списывает повторно | E2E duplicate payment | выполнено |
| Конкурентность | атомарный conditional SQL update; два заказа 400 оставляют 200 | E2E concurrency | выполнено |
| Gateway | `/payments/**`, `/orders/**`, передача identity | Gateway routes/filters + E2E | выполнено |
| SQL-аналитика | количество и сумма `PAID` по `user_id` | `docs/analytics.sql` | выполнено |
| Чек-лист 7.1 | все пять сценариев автоматизированы | `autotests`, 7/7 PASS | выполнено |
| Все обязательные endpoints | минимум один успешный системный сценарий для 2.3 и 3.5 | Rest Assured | выполнено |
| Allure integration | результаты создаются Maven adapter | `autotests/pom.xml`, локальный `target/allure-results` | выполнено |
| C4 | C1/C2 исходники корректны; PDF отсутствуют | `docs/*.puml` | требуется экспорт PDF |
| Публичный репозиторий тестов | независимый Maven-проект готов, но URL не опубликован | `autotests/` | требуется публикация |
| Security triage | 9 строк и комментарии к двум рискам | `docs/security-triage.md` | выполнено |
| Gitleaks/Semgrep | реальный scan, 2 findings разобраны как FP/accepted build risk | JSON reports + security triage | выполнено |

## Дополнительная реализация сверх минимального ТЗ

- Auth Service: BCrypt, JWT, профиль и доверенная identity через Gateway.
- Notifications Service: Kafka, история, read/read-all и SSE.
- Rust Geo Pricing Service: GeoJSON, площадь, самопересечения, bbox, тариф, HTTP и gRPC API.
- React/TypeScript/Leaflet: карта AOI, pan/zoom, полигон и динамическая цена.
- Формирование спутникового продукта после оплаты с маской исходного AOI.
- Blob-хранилище IndexedDB вместо ограниченного `localStorage`.
- Redis-кэш с TTL/AOF и инвалидацией.
- Liquibase, расширенные индексы и PostgreSQL tuning.
- Единый Swagger UI для четырёх Java REST API.

## Найдено и исправлено во время аудита

1. Неверный UUID в Orders возвращал 500 — теперь единый JSON 400 `INVALID_ORDER_ID`.
2. Notifications без `X-User-Id` возвращал 500 — теперь JSON 400 `MISSING_USER_ID`.
3. Неизвестный notification id и неверный UUID получили корректные 404/400.
4. Malformed JSON Auth и конкурентный duplicate email получили 400/409 вместо 500.
5. Удалена ссылка frontend на отсутствующий `earth-night.jpg`; сборка проходит без этого предупреждения.
6. Browser E2E расширен до оплаты, открытия продукта и проверки Blob в IndexedDB.
7. Удалены ложные заявления документации о несуществующих security JSON.

## Фактический прогон

| Команда/проверка | Результат |
|---|---|
| `mvn clean test` | 12 tests, 0 failures/errors |
| `mvn spotless:check` | PASS |
| `mvn -f autotests/pom.xml test` | 7 tests, 0 failures/errors |
| Rust fmt + clippy `-D warnings` + test | PASS, 2 tests |
| frontend format + TypeScript/Vite build | PASS |
| browser product E2E | PASS: AOI, quote, PAID, image, IndexedDB |
| Swagger UI + 4 OpenAPI JSON | HTTP 200 |
| Docker Compose | 10 containers Up, PostgreSQL/Redis healthy |

## Ограничения продукта, не нарушающие минимальное ТЗ

- Цена web-заказа вычисляется Rust, но Orders сохраняет присланную клиентом цену; для production следует повторно подтверждать quote сервер-сервер.
- gRPC-сервер Rust реализован, однако текущий web-flow использует HTTP API через Gateway.
- Планировщик повторных Monitoring-снимков отсутствует; исходное ТЗ прямо исключает моделирование расписания.
- Снимки хранятся локально в IndexedDB; production требует object storage и signed URL.
- Imagery зависит от внешнего провайдера и его условий использования.

## Консервативная оценка готовности по рубрике LMS

До оформления оставшихся внешних артефактов: ориентировочно **91/100** без отдельной оценки презентации. Потенциальные недостающие баллы относятся к C4 PDF/оформлению и публичной публикации autotests. После выполнения этих двух действий программная часть имеет основания претендовать на полный балл по рубрике.
