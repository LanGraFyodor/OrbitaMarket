# Последняя верификация

Среда проверки: Windows, Java 21, Maven 3.9.8, Docker Desktop. Все этапы roadmap завершены к 12.07.2026.

| Проверка | Результат |
|---|---|
| `mvn clean test` | 12 tests, 0 failures/errors |
| `mvn spotless:check` | BUILD SUCCESS |
| Rust `fmt/clippy/test` | 2 tests, warnings denied, PASS |
| frontend `npm ci` | 0 vulnerabilities |
| frontend Prettier/build | PASS |
| Compose startup | 10 контейнеров Up; PostgreSQL/Redis healthy |
| Liquibase | 4 схемы × 2 changeset; Hibernate validation PASS |
| PostgreSQL indexes | auth 4, notifications 6, orders 8, payments 9 |
| Redis smoke | ключи `account-balance` и `orders-by-user` созданы; write invalidation проверена |
| API smoke | счёт 1000 → заказ 120 → баланс 880 |
| `OrbitaMarketE2ETest` | 7 tests, 0 failures/errors |
| browser product E2E | 15 successful/visible tiles, 5 AOI SVG paths, quote unlocked order, `PAID` product opened, 1 Blob in IndexedDB |
| Swagger/OpenAPI | единый UI и 4 service documents возвращают HTTP 200 |
| API negative paths | неверный `order_id` и отсутствие identity в Notifications возвращают JSON 400, а не 500 |
| Gitleaks | 291.56 MB scanned, 1 FP test key, 0 confirmed leaks |
| Semgrep | 145 rules, 78 targets, 1 INFO FP/accepted build risk, 0 errors |
| dynamic Rust pricing | 0.52 км² TASKING: 305 GC (0.3 м), 115 GC (0.8 м), 32 GC (3 м) |
| imagery fulfillment | спутниковый JPEG сформирован через Rust, 275648 bytes |

Во время итогового аудита исправлены два необработанных client error: неверный UUID Orders и отсутствующий `X-User-Id` Notifications ранее давали 500. Browser E2E расширен до фактической оплаты и открытия снимка; отдельно подтверждено, что изображение хранится Blob в IndexedDB и не расходует quota `localStorage`.

Полные JSON-результаты сохранены в `docs/gitleaks-report.json` и `docs/semgrep-report.json`; исходные findings не скрывались и разобраны в security triage.
