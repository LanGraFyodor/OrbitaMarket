# OrbitaMarket API autotests

Независимый Maven-проект системных тестов. Он не импортирует внутренние классы микросервисов и обращается к системе только через публичный Gateway и Kafka.

Это автономный проект: при публикации в отдельный публичный Git-репозиторий в него переносятся только этот README, `pom.xml` и `src/`, без исходников микросервисов.

## Запуск

Сначала поднять основное приложение из соседнего репозитория:

```bash
docker compose up --build -d
```

Затем, из отдельного clone этого репозитория:

```bash
mvn clean test
```

Если тесты запускаются из исходного монорепозитория OrbitaMarket, используйте эквивалентную команду `mvn -f autotests/pom.xml clean test`.

Параметры отдельного clone:

```bash
mvn test \
  -Dgateway.url=http://localhost:8080 \
  -Dkafka.bootstrap=localhost:9092
```

Проверяются пять обязательных сценариев 7.1, успешные Payments/Orders endpoints, единый формат ошибок, `REJECTED/failure_reason`, идемпотентность Kafka и конкурентный баланс.

Allure results создаются в `target/allure-results`. Для генерации статического HTML-отчёта Maven самостоятельно скачает совместимый Allure CLI:

```bash
mvn allure:report
```

Готовый отчёт оказывается в `target/allure-report/index.html`. В основном репозитории OrbitaMarket его копия зафиксирована как `docs/allure-report/index.html`.
