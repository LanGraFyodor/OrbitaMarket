package ru.orbitamarket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthServiceTest {
  private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-signing";
  private UserRepository repository;
  private JwtService jwtService;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    repository = mock(UserRepository.class);
    jwtService = new JwtService(SECRET, Duration.ofHours(1));
    authService = new AuthService(repository, jwtService);
    when(repository.save(any(UserAccount.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void registersUserWithHashedPasswordAndUsableToken() {
    when(repository.existsByEmailIgnoreCase("pilot@orbita.ru")).thenReturn(false);

    AuthResponse response =
        authService.register(new RegisterRequest("Pilot@Orbita.Ru", "strong-pass-42", "Pilot"));

    assertEquals("pilot@orbita.ru", response.profile().email());
    assertEquals(response.profile().userId(), jwtService.verify(response.accessToken()));
    ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
    verify(repository).save(captor.capture());
    assertNotEquals("strong-pass-42", captor.getValue().passwordHash());
  }

  @Test
  void rejectsInvalidPasswordWithoutRevealingWhichCredentialFailed() {
    UserAccount user =
        new UserAccount(
            UUID.randomUUID(), "pilot@orbita.ru", "$2a$12$invalid", "Pilot", Instant.now());
    when(repository.findByEmailIgnoreCase("pilot@orbita.ru")).thenReturn(Optional.of(user));

    AuthException exception =
        assertThrows(
            AuthException.class,
            () -> authService.login(new LoginRequest("pilot@orbita.ru", "wrong-password")));

    assertEquals("UNAUTHORIZED", exception.code());
    assertEquals("Invalid email or password", exception.getMessage());
  }

  @Test
  void readsAndUpdatesOnlyProfileIdentifiedByJwtSubject() {
    UUID userId = UUID.randomUUID();
    UserAccount user = new UserAccount(userId, "pilot@orbita.ru", "unused", "Pilot", Instant.now());
    String token = jwtService.issue(user);
    when(repository.findById(userId)).thenReturn(Optional.of(user));

    ProfileResponse updated =
        authService.update(
            "Bearer " + token,
            new UpdateProfileRequest("Alex Pilot", "Operator", "Orbita", "+7", "LEO"));

    assertEquals(userId, updated.userId());
    assertEquals("Alex Pilot", updated.displayName());
    assertEquals("Operator", authService.profile("Bearer " + token).jobTitle());
  }
}
