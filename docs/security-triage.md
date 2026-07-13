# Security triage

Финальный инструментальный прогон выполнен 13.07.2026 официальными Docker-образами. Gitleaks проверил текущее содержимое проекта, Semgrep проверил Java, TypeScript и Rust с включением незакоммиченных файлов и исключением только build-кэшей.

## Статус инструментальных проверок

| Инструмент | Результат | Файл |
|---|---:|---|
| Gitleaks | 1 finding, 0 подтверждённых утечек после триажа | `docs/gitleaks-report.json` |
| Semgrep OSS | 145 rules, 78 targets, 1 INFO finding, 0 errors | `docs/semgrep-report.json` |

Рекомендуемые команды:

```bash
docker run --rm -v "${PWD}:/repo" ghcr.io/gitleaks/gitleaks:latest dir /repo --report-format json --report-path /repo/docs/gitleaks-report.json --redact --exit-code 0
docker run --rm -v "${PWD}:/src" semgrep/semgrep:latest semgrep scan --config p/java --config p/typescript --config p/rust --metrics off --no-git-ignore --exclude .git --exclude "**/target/**" --exclude "**/node_modules/**" --exclude "**/dist/**" --json --output /src/docs/semgrep-report.json /src
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
| SEC-07 | frontend | готовые PNG хранятся как Blob в IndexedDB браузера | Low | risk | quota localStorage устранена; для production использовать private object storage и signed URL |
| SEC-08 | Gitleaks | `SECRET = "test-secret-key..."` в `AuthServiceTest` | Info | FP | публичная фиктивная тестовая строка без доступа к окружению; production secret не содержит |
| SEC-09 | Semgrep | `unsafe` для `std::env::set_var("PROTOC", ...)` в Rust `build.rs` | Info | FP / accepted build risk | только build script; путь берётся из `protoc-bin-vendored`, runtime unsafe отсутствует |

SQL выполняется через JPA/параметризованные запросы. Пароли пользователей хранятся как BCrypt-хеши. JWT-профиль имеет ограниченный TTL; Gateway проверяет подпись и при валидном токене перезаписывает клиентский `X-User-Id` значением `sub`.

## Комментарии к двум строкам триажа

**SEC-01 — risk, а не подтверждённая утечка.** Значение по умолчанию явно обозначено как development secret и необходимо для запуска учебного Compose без ручной настройки. Оно становится реальной уязвимостью, если тот же ключ попадёт в публичное окружение, поэтому production должен требовать внешний `JWT_SECRET` и не иметь рабочего значения по умолчанию.

**SEC-05 — осознанный архитектурный риск.** Ручной `X-User-Id` сохранён для обязательных автотестов и локального сценария из технического задания. Gateway корректно перезаписывает его значением `sub` при наличии валидного JWT, но production ingress должен полностью запретить legacy-доступ без токена.

**SEC-08 — FP.** Gitleaks корректно распознал строку, похожую на API key, но она объявлена только в unit-тесте, не используется в Compose или deployed-конфигурации и не соответствует секрету внешнего сервиса. Исходный finding сохранён в JSON, решение зафиксировано только в триаже.

**SEC-09 — FP/accepted build risk.** Semgrep реагирует на ключевое слово `unsafe`, однако блок нужен из-за контракта Rust 2024 для изменения environment. Он выполняется в однопоточном Cargo build script и задаёт путь к vendored `protoc`; пользовательские данные туда не попадают, а в runtime-бинарнике этот блок отсутствует.
