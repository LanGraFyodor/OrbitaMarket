# Чек-лист приёмки и тестирования

## Обязательные сценарии раздела 7.1

| № | Сценарий | Ожидаемый результат | Автоматизация |
|---:|---|---|---|
| 1 | счёт → пополнение 1000 → заказ 120 | `PAID`, баланс 880 | `happyPath` |
| 2 | баланс 50 → заказ 120 | `PAYMENT_FAILED`, баланс 50 | `insufficientFunds` |
| 3 | повтор `OrderPaymentRequested` с тем же `order_id` | повторного списания нет | `duplicatePaymentCommandIsIdempotent` |
| 4 | два заказа по 400 при балансе 1000 | итог 200, баланс не отрицательный | `concurrentOrdersKeepBalanceCorrect` |
| 5 | повторный `POST /accounts` | второй счёт не создаётся | `repeatedAccountCreationDoesNotCreateDuplicate` |

## REST и дополнительные проверки

| Проверка | Ожидаемый результат |
|---|---|
| `POST /accounts`, `POST /accounts/top-up`, `GET /accounts/balance` | успешные ответы и правильный баланс |
| `POST /orders`, `GET /orders`, `GET /orders/{id}` | создание, список и детали текущего пользователя |
| ARCHIVE/TASKING/MONITORING payload | корректные поля принимаются, неверные → `REJECTED`/ошибка |
| `amount <= 0`, неверный JSON | единый `error_code/message/timestamp` |
| заказ другого пользователя | 404 `ORDER_NOT_FOUND` |
| карта | обычные DOM-тайлы видны, zoom/pan работают |
| AOI | вершины/линия/полигон видны, до подтверждения заказ заблокирован |
| geo quote | площадь и цена приходят от Rust, а не из frontend-константы |
| динамическая цена | при одном AOI цена меняется с разрешением; при одном разрешении — с площадью; zoom не влияет |
| fulfillment | после `PAID` снимок доступен для любого корректного AOI, сохраняет Web Mercator aspect и маскируется по полигону |
| retry fulfillment | камера оплаченного заказа повторно формирует продукт, если локального кадра ещё нет |
| уведомления | результат оплаты появляется в истории и SSE-колокольчике |

## Команды перед публикацией

```bash
mvn clean test
mvn spotless:check
cd geo-pricing-service && cargo fmt --check && cargo clippy --all-targets -- -D warnings && cargo test
cd frontend && npm ci && npm run build
docker compose up --build -d
mvn -f autotests/pom.xml test
cd frontend && npm run test:map
docker compose down
```

Allure CLI не входит в репозиторий. При наличии: `allure serve autotests/target/allure-results`.
