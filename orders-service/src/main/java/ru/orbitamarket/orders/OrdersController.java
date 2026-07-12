package ru.orbitamarket.orders;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/orders")
class OrdersController {

  private final OrderService orderService;

  OrdersController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  ResponseEntity<OrderResponse> create(
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @RequestBody CreateOrderRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(userId, request));
  }

  @GetMapping
  List<OrderResponse> list(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return orderService.list(userId);
  }

  @GetMapping("/{orderId}")
  OrderResponse get(
      @RequestHeader(value = "X-User-Id", required = false) String userId,
      @PathVariable UUID orderId) {
    return orderService.get(userId, orderId);
  }
}
