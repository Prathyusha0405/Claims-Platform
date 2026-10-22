package com.chubb.claims.auth.controller;

import com.chubb.claims.auth.dto.UserResponse;
import com.chubb.claims.auth.security.AuthenticatedUser;
import com.chubb.claims.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the profile of the authenticated user")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getCurrentUser(principal.userId());
    }
}
