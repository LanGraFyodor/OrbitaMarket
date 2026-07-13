# Поток оплаты и гарантии доставки

```text
Client → POST /orders
  → Orders transaction: order=CREATED + outbox(OrderPaymentRequested)
  → HTTP 201 (клиент не ждёт оплату)

Orders outbox worker → Kafka orders.payment-requests
  → broker ACK
  → order=PAYMENT_PENDING, outbox=published

Payments consumer transaction
  → INSERT inbox(event_id); duplicate → ACK без повторного эффекта
  → conditional UPDATE accounts SET balance=balance-amount
       WHERE user_id=? AND balance>=amount
  → INSERT payment(order_id UNIQUE)
  → INSERT outbox(OrderPaymentCompleted | OrderPaymentFailed)

Payments outbox worker → Kafka payments.results
  → Orders inbox(event_id) → сверка user_id/amount/ожидаемого статуса → PAID | PAYMENT_FAILED
  → Notifications inbox/event uniqueness → SSE/история
```

Kafka предоставляет at-least-once доставку. Transactional outbox не позволяет потерять событие между commit БД и публикацией; inbox и уникальный `payments.order_id` превращают повторную доставку в **effectively exactly-once** финансовый эффект.

Конкурентность защищена одним условным SQL update: проверка остатка и списание выполняются атомарно. Пополнение также атомарное. Небезопасного read-modify-write нет, отрицательный баланс невозможен.

## Статусы заказа

```text
CREATED → PAYMENT_PENDING → PAID
                          ↘ PAYMENT_FAILED (failure_reason)
CREATED → REJECTED (ошибка payload/type/price, failure_reason)
```

Дубли результата оплаты игнорируются inbox Orders. Перед переходом статуса Orders также сверяет пользователя, сумму и то, что заказ ещё ожидает результат; нерелевантное или запоздалое событие не меняет заказ. Заказ другого пользователя возвращается как `ORDER_NOT_FOUND`, не раскрывая его существование.
