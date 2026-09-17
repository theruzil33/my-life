package com.theruzil.gateway.auth;

import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final JwtService jwtService;

    public AuthController(RegistrationService registrationService, LoginService loginService, JwtService jwtService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody RegisterRequest request) {
        User user = registrationService.register(request.email(), request.password());
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        String accessToken = loginService.login(request.email(), request.password());
        return new TokenResponse(accessToken, "Bearer", jwtService.getAccessTokenTtl().toSeconds());
    }
}
