package ru.orbitamarket.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class JwtService {
  private final SecretKey key;
  private final Duration ttl;

  JwtService(@Value("${auth.jwt.secret}") String secret, @Value("${auth.jwt.ttl}") Duration ttl) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = ttl;
  }

  String issue(UserAccount user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.id().toString())
        .claim("email", user.email())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  UUID verify(String token) {
    Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    return UUID.fromString(claims.getSubject());
  }

  long expiresInSeconds() {
    return ttl.toSeconds();
  }
}
