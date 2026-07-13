package ru.orbitamarket.autotests;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

@Epic("OrbitaMarket")
@Feature("Заказ и асинхронная оплата через Gateway")
class OrbitaMarketE2ETest {

    private static final String GATEWAY = System.getProperty("gateway.url", "http://localhost:8080");
    private static final String KAFKA = System.getProperty("kafka.bootstrap", "localhost:9092");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @Story("Happy path: баланс 1000, заказ 120, результат PAID и баланс 880")
    void happyPath() {
        String user = newUser();
        createAccount(user, 201);
        topUp(user, 1000);

        String orderId = createArchiveOrder(user, 120);

        awaitStatus(user, orderId, "PAID");
        assertThat(balance(user)).isEqualTo(880);
        assertThat(listOrders(user).jsonPath().getList("order_id", String.class)).contains(orderId);
    }

    @Test
    @Story("Недостаточно средств: заказ отклоняется оплатой, баланс не меняется")
    void insufficientFunds() {
        String user = newUser();
        createAccount(user, 201);
        topUp(user, 50);

        String orderId = createArchiveOrder(user, 120);

        Response order = awaitStatus(user, orderId, "PAYMENT_FAILED");
        assertThat(order.jsonPath().getString("failure_reason")).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(balance(user)).isEqualTo(50);
    }

    @Test
    @Story("Повторная команда оплаты по order_id не списывает баланс повторно")
    void duplicatePaymentCommandIsIdempotent() throws Exception {
        String user = newUser();
        createAccount(user, 201);
        topUp(user, 1000);
        String orderId = createArchiveOrder(user, 120);
        awaitStatus(user, orderId, "PAID");
        long balanceAfterPayment = balance(user);

        publishPaymentRequest(orderId, user, 120);

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(balance(user)).isEqualTo(balanceAfterPayment));
    }

    @Test
    @Story("Два параллельных заказа не приводят к отрицательному или потерянному балансу")
    void concurrentOrdersKeepBalanceCorrect() {
        String user = newUser();
        createAccount(user, 201);
        topUp(user, 1000);

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> createArchiveOrder(user, 400));
        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> createArchiveOrder(user, 400));

        awaitStatus(user, first.join(), "PAID");
        awaitStatus(user, second.join(), "PAID");
        assertThat(balance(user)).isEqualTo(200);
    }

    @Test
    @Story("Повторное создание счёта идемпотентно")
    void repeatedAccountCreationDoesNotCreateDuplicate() {
        String user = newUser();
        createAccount(user, 201);
        createAccount(user, 200);
        assertThat(balance(user)).isZero();
    }

    @Test
    @Story("Невалидный заказ сохраняется в статусе REJECTED с failure_reason")
    void invalidOrderIsPersistedAsRejected() {
        String user = newUser();
        Map<String, Object> invalidPayload = Map.of("aoi", "POINT(30 60)");

        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body(Map.of("product_type", "ARCHIVE", "price", 120, "payload", invalidPayload))
                .when().post(GATEWAY + "/orders/api/v1/orders/orders")
                .then().statusCode(400).body("error_code", org.hamcrest.Matchers.equalTo("INVALID_PAYLOAD"));

        Response orders = listOrders(user);
        assertThat(orders.jsonPath().getList("status", String.class)).contains("REJECTED");
        assertThat(orders.jsonPath().getList("failure_reason", String.class))
                .contains("INVALID_PAYLOAD");
    }

    @Test
    @Story("Все HTTP-ошибки возвращаются в едином формате")
    void errorResponsesFollowContract() {
        String user = newUser();

        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body("{\"amount\":\"not-a-number\"}")
                .when().post(GATEWAY + "/payments/api/v1/payments/accounts/top-up")
                .then().statusCode(400)
                .body("error_code", org.hamcrest.Matchers.equalTo("INVALID_AMOUNT"))
                .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString()))
                .body("timestamp", org.hamcrest.Matchers.notNullValue());

        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body(Map.of("product_type", "UNKNOWN", "price", 120, "payload", Map.of("aoi", "x")))
                .when().post(GATEWAY + "/orders/api/v1/orders/orders")
                .then().statusCode(400)
                .body("error_code", org.hamcrest.Matchers.equalTo("UNKNOWN_PRODUCT_TYPE"));

        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body("{broken-json")
                .when().post(GATEWAY + "/orders/api/v1/orders/orders")
                .then().statusCode(400)
                .body("error_code", org.hamcrest.Matchers.equalTo("INVALID_PAYLOAD"));

        given().when().get(GATEWAY + "/payments/api/v1/payments/accounts/balance")
                .then().statusCode(400)
                .body("error_code", org.hamcrest.Matchers.equalTo("MISSING_USER_ID"));

        given().header("X-User-Id", user)
                .when().get(GATEWAY + "/orders/api/v1/orders/orders/" + UUID.randomUUID())
                .then().statusCode(404)
                .body("error_code", org.hamcrest.Matchers.equalTo("ORDER_NOT_FOUND"));

        given().header("X-User-Id", user)
                .when().get(GATEWAY + "/orders/api/v1/orders/orders/not-a-uuid")
                .then().statusCode(400)
                .body("error_code", org.hamcrest.Matchers.equalTo("INVALID_ORDER_ID"));

        given().when().get(GATEWAY + "/notifications/api/v1/notifications")
                .then().statusCode(400)
                .body("error_code", org.hamcrest.Matchers.equalTo("MISSING_USER_ID"));
    }

    private static String newUser() {
        return "e2e-" + UUID.randomUUID();
    }

    private static void createAccount(String user, int status) {
        given().header("X-User-Id", user)
                .when().post(GATEWAY + "/payments/api/v1/payments/accounts")
                .then().statusCode(status);
    }

    private static void topUp(String user, long amount) {
        given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body(Map.of("amount", amount))
                .when().post(GATEWAY + "/payments/api/v1/payments/accounts/top-up")
                .then().statusCode(200);
    }

    private static long balance(String user) {
        return given().header("X-User-Id", user)
                .when().get(GATEWAY + "/payments/api/v1/payments/accounts/balance")
                .then().statusCode(200).extract().jsonPath().getLong("balance");
    }

    private static String createArchiveOrder(String user, long price) {
        Map<String, Object> payload = Map.of(
                "aoi", "POINT(30 60)",
                "capture_date", "2026-07-01",
                "sensor_type", "MSI");
        return given().header("X-User-Id", user).contentType(ContentType.JSON)
                .body(Map.of("product_type", "ARCHIVE", "price", price, "payload", payload))
                .when().post(GATEWAY + "/orders/api/v1/orders/orders")
                .then().statusCode(201)
                .body("status", org.hamcrest.Matchers.equalTo("CREATED"))
                .extract().jsonPath().getString("order_id");
    }

    private static Response awaitStatus(String user, String orderId, String expectedStatus) {
        final Response[] result = new Response[1];
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response response = given().header("X-User-Id", user)
                    .when().get(GATEWAY + "/orders/api/v1/orders/orders/" + orderId);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getString("status")).isEqualTo(expectedStatus);
            result[0] = response;
        });
        return result[0];
    }

    private static Response listOrders(String user) {
        return given().header("X-User-Id", user)
                .when().get(GATEWAY + "/orders/api/v1/orders/orders")
                .then().statusCode(200).extract().response();
    }

    private static void publishPaymentRequest(String orderId, String user, long amount) throws Exception {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", KAFKA);
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        Map<String, Object> event = Map.of(
                "event_id", UUID.randomUUID().toString(),
                "order_id", orderId,
                "user_id", user,
                "amount", amount,
                "occurred_at", Instant.now().toString());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>("orders.payment-requests", orderId, JSON.writeValueAsString(event))).get();
        }
    }
}
