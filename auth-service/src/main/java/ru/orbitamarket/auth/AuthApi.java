package ru.orbitamarket.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @JsonProperty("display_name") @NotBlank @Size(max = 100) String displayName) {}

record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

record UpdateProfileRequest(
    @JsonProperty("display_name") @NotBlank @Size(max = 100) String displayName,
    @JsonProperty("job_title") @Size(max = 100) String jobTitle,
    @Size(max = 120) String company,
    @Size(max = 30) String phone,
    @Size(max = 500) String bio) {}

record ProfileResponse(
    @JsonProperty("user_id") UUID userId,
    String email,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("job_title") String jobTitle,
    String company,
    String phone,
    String bio,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt) {
  static ProfileResponse from(UserAccount user) {
    return new ProfileResponse(
        user.id(),
        user.email(),
        user.displayName(),
        user.jobTitle(),
        user.company(),
        user.phone(),
        user.bio(),
        user.createdAt(),
        user.updatedAt());
  }
}

record AuthResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    ProfileResponse profile) {}

record AuthError(@JsonProperty("error_code") String errorCode, String message, Instant timestamp) {}

@Service
class AuthService {
  private final UserRepository repository;
  private final JwtService jwtService;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

  AuthService(UserRepository repository, JwtService jwtService) {
    this.repository = repository;
    this.jwtService = jwtService;
  }

  @Transactional
  AuthResponse register(RegisterRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (repository.existsByEmailIgnoreCase(email)) {
      throw new AuthException(
          HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email already registered");
    }
    UserAccount user =
        repository.save(
            new UserAccount(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                Instant.now()));
    return response(user);
  }

  @Transactional(readOnly = true)
  AuthResponse login(LoginRequest request) {
    UserAccount user =
        repository
            .findByEmailIgnoreCase(request.email().trim())
            .orElseThrow(() -> unauthorized("Invalid email or password"));
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw unauthorized("Invalid email or password");
    }
    return response(user);
  }

  @Transactional(readOnly = true)
  ProfileResponse profile(String authorization) {
    return ProfileResponse.from(current(authorization));
  }

  @Transactional
  ProfileResponse update(String authorization, UpdateProfileRequest request) {
    UserAccount user = current(authorization);
    user.updateProfile(
        request.displayName().trim(),
        clean(request.jobTitle()),
        clean(request.company()),
        clean(request.phone()),
        clean(request.bio()));
    return ProfileResponse.from(user);
  }

  private UserAccount current(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer "))
      throw unauthorized("Bearer token required");
    try {
      UUID id = jwtService.verify(authorization.substring(7));
      return repository.findById(id).orElseThrow(() -> unauthorized("User no longer exists"));
    } catch (JwtException | IllegalArgumentException exception) {
      throw unauthorized("Token is invalid or expired");
    }
  }

  private AuthResponse response(UserAccount user) {
    return new AuthResponse(
        jwtService.issue(user),
        "Bearer",
        jwtService.expiresInSeconds(),
        ProfileResponse.from(user));
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static AuthException unauthorized(String message) {
    return new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
  }
}

@RestController
@RequestMapping("/api/v1")
class AuthController {
  private final AuthService authService;

  AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/auth/register")
  ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
  }

  @PostMapping("/auth/login")
  AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @GetMapping("/profile")
  @SecurityRequirement(name = "bearerAuth")
  ProfileResponse profile(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return authService.profile(authorization);
  }

  @PutMapping("/profile")
  @SecurityRequirement(name = "bearerAuth")
  ProfileResponse update(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody UpdateProfileRequest request) {
    return authService.update(authorization, request);
  }
}

class AuthException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  AuthException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  HttpStatus status() {
    return status;
  }

  String code() {
    return code;
  }
}

@RestControllerAdvice
class AuthExceptionHandler {
  @ExceptionHandler(AuthException.class)
  ResponseEntity<AuthError> handle(AuthException exception) {
    return ResponseEntity.status(exception.status())
        .body(new AuthError(exception.code(), exception.getMessage(), Instant.now()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<AuthError> invalid() {
    return ResponseEntity.badRequest()
        .body(
            new AuthError(
                "INVALID_REQUEST", "Check required fields and password length", Instant.now()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<AuthError> malformedJson() {
    return ResponseEntity.badRequest()
        .body(new AuthError("INVALID_REQUEST", "Malformed JSON request", Instant.now()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<AuthError> duplicateEmail() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new AuthError("EMAIL_ALREADY_EXISTS", "Email already registered", Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<AuthError> internal() {
    return ResponseEntity.internalServerError()
        .body(new AuthError("INTERNAL_ERROR", "Unexpected server error", Instant.now()));
  }
}
