package com.theruzil.gateway.auth;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.theruzil.gateway.auth.dto.LoginRequest;
import com.theruzil.gateway.auth.dto.RegisterRequest;
import com.theruzil.gateway.auth.dto.TokenResponse;
import com.theruzil.gateway.auth.dto.UserResponse;
import com.theruzil.gateway.user.User;
import com.theruzil.gateway.user.UserRepository;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    private final UserRepository userRepository;

    public AuthController(RegistrationService registrationService, LoginService loginService, JwtService jwtService,
            RefreshTokenService refreshTokenService, RefreshTokenCookieFactory refreshTokenCookieFactory,
            UserRepository userRepository) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody RegisterRequest request) {
        User user = registrationService.register(request.email(), request.password());
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        User user = loginService.login(request.email(), request.password());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieHeader(refreshToken))
                .body(TokenResponse.of(jwtService.issueAccessToken(user.getId()), jwtService.getAccessTokenTtl(),
                        user.getEmail()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieHeader(rotation.value()))
                .body(TokenResponse.of(jwtService.issueAccessToken(user.getId()), jwtService.getAccessTokenTtl(),
                        user.getEmail()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clearing().toString())
                .build();
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken(), "headerName", csrfToken.getHeaderName());
    }

    private String cookieHeader(RefreshTokenService.IssuedRefreshToken refreshToken) {
        return cookieHeader(refreshToken.value());
    }

    private String cookieHeader(String value) {
        return refreshTokenCookieFactory.create(value, refreshTokenService.getRefreshTokenTtl()).toString();
    }
}
