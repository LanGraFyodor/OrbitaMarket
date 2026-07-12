package ru.orbitamarket.gateway;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
class JwtIdentityFilter implements GlobalFilter, Ordered {
  private static final byte[] UNAUTHORIZED =
      "{\"error_code\":\"UNAUTHORIZED\",\"message\":\"Token is invalid or expired\"}"
          .getBytes(StandardCharsets.UTF_8);

  private final SecretKey key;

  JwtIdentityFilter(@Value("${auth.jwt.secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return chain.filter(exchange);
    }

    try {
      String subject =
          Jwts.parser()
              .verifyWith(key)
              .build()
              .parseSignedClaims(authorization.substring(7))
              .getPayload()
              .getSubject();
      String userId = UUID.fromString(subject).toString();
      ServerWebExchange identified =
          exchange
              .mutate()
              .request(request -> request.headers(headers -> headers.set("X-User-Id", userId)))
              .build();
      return chain.filter(identified);
    } catch (JwtException | IllegalArgumentException exception) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
      return exchange
          .getResponse()
          .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(UNAUTHORIZED)));
    }
  }

  @Override
  public int getOrder() {
    return -100;
  }
}
