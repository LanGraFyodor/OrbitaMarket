package ru.orbitamarket.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Transport-only filter that explicitly retains the caller identity header during proxying. */
@Component
class HeaderForwardingFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
    if (userId == null || userId.isBlank()) {
      return chain.filter(exchange);
    }

    ServerWebExchange forwarded =
        exchange
            .mutate()
            .request(request -> request.headers(headers -> headers.set("X-User-Id", userId)))
            .build();
    return chain.filter(forwarded);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }
}
