-- Выполнять в схеме Orders. Кто и сколько купил: только успешно оплаченные заказы.
SET search_path TO orders;
SELECT user_id,
       COUNT(*) AS paid_orders_count,
       SUM(price) AS total_spent_geocredits
FROM orders
WHERE status = 'PAID'
GROUP BY user_id
ORDER BY total_spent_geocredits DESC;

-- Доля неуспешных списаний по типу продукта.
SELECT type,
       COUNT(*) FILTER (WHERE status = 'PAID') AS paid_count,
       COUNT(*) FILTER (WHERE status = 'PAYMENT_FAILED') AS failed_count
FROM orders
GROUP BY type
ORDER BY type;
