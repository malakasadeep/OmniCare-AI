package com.omnicare.platform.tenant.api;

import com.omnicare.platform.shared.domain.Email;
import com.omnicare.platform.shared.domain.InvalidEmailException;
import com.omnicare.platform.tenant.application.AuthService;
import com.omnicare.platform.tenant.application.InvalidCredentialsException;
import com.omnicare.platform.tenant.application.TokenPair;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenPair tokens = authService.register(
                request.companyName(), new Email(request.email()), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(TokenResponse.from(tokens));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Email email;
        try {
            email = new Email(request.email());
        } catch (InvalidEmailException e) {
            // A malformed address is simply a login that cannot match anything.
            throw new InvalidCredentialsException();
        }
        return TokenResponse.from(authService.login(email, request.password()));
    }
}
