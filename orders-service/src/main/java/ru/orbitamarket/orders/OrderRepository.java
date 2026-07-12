package ru.orbitamarket.orders;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderRepository extends JpaRepository<Order, UUID> {

  List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

  @Modifying(flushAutomatically = true)
  @Query(
      """
      update Order orders
         set orders.status = ru.orbitamarket.orders.OrderStatus.PAYMENT_PENDING
       where orders.id = :orderId
         and orders.status = ru.orbitamarket.orders.OrderStatus.CREATED
      """)
  int markPaymentPending(@Param("orderId") UUID orderId);
}
