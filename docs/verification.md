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
| browser map E2E | 25 successful tiles, 15 visible images, 5 AOI SVG paths, quote unlocked order |
| dynamic Rust pricing | 0.52 км² TASKING: 305 GC (0.3 м), 115 GC (0.8 м), 32 GC (3 м) |
| imagery fulfillment | спутниковый JPEG сформирован через Rust, 275648 bytes |

Во время первого E2E после Redis был найден и исправлен null cache key: запрос без `X-User-Id` снова возвращает `400 MISSING_USER_ID`, а не 500. Во время browser E2E найден и исправлен stale Docker DNS nginx после пересоздания Gateway.

Gitleaks/Semgrep CLI в текущей среде не установлены; в `docs/` лежат сохранённые JSON предыдущего чистого прогона. Перед защитой их следует повторить на финальном commit командами из `docs/security-triage.md`.
