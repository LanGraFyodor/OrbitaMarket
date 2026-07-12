# OrbitaMarket API autotests

Независимый Maven-проект системных тестов. Он не импортирует внутренние классы микросервисов и обращается к системе только через публичный Gateway и Kafka.

Для выполнения требования курса каталог `autotests` следует опубликовать отдельным публичным Git-репозиторием. В отдельном репозитории сохраните этот README и `pom.xml/src` без копирования исходников сервисов.

## Запуск

Сначала поднять основное приложение из соседнего репозитория:

```bash
docker compose up --build -d
```

Затем:

```bash
mvn -f autotests/pom.xml clean test
```

Параметры:

```bash
mvn -f autotests/pom.xml test \
  -Dgateway.url=http://localhost:8080 \
  -Dkafka.bootstrap=localhost:9092
```

Проверяются пять обязательных сценариев 7.1, успешные Payments/Orders endpoints, единый формат ошибок, `REJECTED/failure_reason`, идемпотентность Kafka и конкурентный баланс.

Allure results создаются в `autotests/target/allure-results`. При установленном Allure CLI:

```bash
allure serve autotests/target/allure-results
```
