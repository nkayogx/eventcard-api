package com.kayogx.eventcard.controller;

import com.kayogx.eventcard.dto.LoginRequest;
import com.kayogx.eventcard.dto.LoginResponse;
import com.kayogx.eventcard.dto.MeResponse;
import com.kayogx.eventcard.dto.SignupCompanyRequest;
import com.kayogx.eventcard.service.AuthService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Public: a new vendor creates their company and owner account. */
    @PostMapping("/api/auth/signup-company")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse signupCompany(@Valid @RequestBody SignupCompanyRequest request) {
        return authService.signupCompany(request);
    }

    /** Public: log in with email and password. */
    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Anyone logged in: who am I, what is my role, which company am I in? */
    @GetMapping("/api/me")
    public MeResponse whoAmI() {
        return authService.whoAmI();
    }
}
