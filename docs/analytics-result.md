# Результат SQL-аналитики

Запрос из [analytics.sql](analytics.sql) выполнен в PostgreSQL контейнера `orbita-postgres`, базе `orbita`, схеме `orders`:

```sql
SET search_path TO orders;
SELECT user_id,
       COUNT(*) AS paid_orders_count,
       SUM(price) AS total_spent_geocredits
FROM orders
WHERE status = 'PAID'
GROUP BY user_id
ORDER BY total_spent_geocredits DESC;
```

Фрагмент фактического вывода (10 строк):

| user_id | paid_orders_count | total_spent_geocredits |
|---|---:|---:|
| `93aaa538-8f3f-4126-bfad-6b1bd99bf0be` | 8 | 11 904 |
| `e2e-5f815820-a8ec-41f2-934b-445c76bb1438` | 2 | 800 |
| `e2e-f21185bb-73ac-4063-b72d-72efce9bc712` | 2 | 800 |
| `e2e-b1461918-0dca-472d-ba31-0ad3d493a619` | 2 | 800 |
| `e2e-2a9615ac-662e-4969-acd0-c957d6dbd916` | 2 | 800 |
| `e2e-6fb4d6e3-7091-43d4-8705-d9208fa27c6d` | 2 | 800 |
| `d39a0ce3-6eb7-4b19-a675-81d5e56b94d7` | 1 | 312 |
| `0f7e9341-11f7-4a8b-bbfd-4d1cb310c576` | 1 | 307 |
| `e2e-598e19c2-64ed-4375-9e48-47bdae16f671` | 1 | 120 |
| `e2e-972faa61-ab65-4ad3-9487-da8712d8e044` | 1 | 120 |

Результат содержит данные реального локального E2E-прогона и поэтому при повторном запуске может отличаться по UUID и количеству строк. Состав колонок и способ получения остаются неизменными.
