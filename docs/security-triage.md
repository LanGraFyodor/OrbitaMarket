# Security triage

Последний сохранённый инструментальный прогон: 11.07.2026. Перед публикацией/защитой рекомендуется повторить команды на финальном commit.

## Сохранённые результаты

| Инструмент | Результат | Файл |
|---|---:|---|
| Gitleaks | 0 утечек | `docs/gitleaks-report.json` |
| Semgrep OSS | 0 findings, 0 errors в проверенной Java-версии | `docs/semgrep-report.json` |

Рекомендуемые команды:

```bash
gitleaks detect --source . --report-format json --report-path docs/gitleaks-report.json
semgrep scan --config p/java --config p/typescript --config p/rust --json --output docs/semgrep-report.json .
```

## Таблица триажа

| ID | Источник | Наблюдение | Критичность | Класс | Решение |
|---|---|---|---|---|---|
| SEC-01 | architecture | Compose содержит development JWT secret по умолчанию | High | risk | production требует обязательный `JWT_SECRET` из secrets manager |
| SEC-02 | architecture | PostgreSQL login/password `orbita` опубликованы для локального запуска | Medium | risk | не использовать вне localhost; отдельные роли и Docker secrets |
| SEC-03 | Liquibase | ошибочное изменение уже применённого changeset вызовет checksum mismatch | Medium | risk | changeset неизменяемы; исправления только новым id, backup перед production migration |
| SEC-04 | architecture | локальные HTTP/Kafka соединения без TLS и ACL | Medium | risk | TLS на ingress, mTLS/ACL Kafka, внутренние порты не публиковать |
| SEC-05 | Gateway | legacy API допускает ручной `X-User-Id` без JWT | High | risk | сохранено для требований автотестов; production route обязан требовать JWT |
| SEC-06 | imagery | внешний World Imagery влияет на доступность и лицензирование продукта | Medium | risk | timeout/retry/cache, лицензированный provider, зафиксировать attribution/terms |
| SEC-07 | frontend | готовые PNG хранятся в `localStorage` как data URL | Medium | risk | для production использовать private object storage и signed URL |
| SEC-08 | Gitleaks | сохранённый отчёт пустой | Info | FP/clean scan | утечек в проверенной версии не обнаружено; повторить на финальном commit |
| SEC-09 | Semgrep | сохранённый отчёт без findings | Info | FP/clean scan | ручной triage всё равно обязателен; расширить rulesets на TS/Rust |

SQL выполняется через JPA/параметризованные запросы. Пароли пользователей хранятся как BCrypt-хеши. JWT-профиль имеет ограниченный TTL; Gateway проверяет подпись и при валидном токене перезаписывает клиентский `X-User-Id` значением `sub`.
