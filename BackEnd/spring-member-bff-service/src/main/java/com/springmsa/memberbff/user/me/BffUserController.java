package com.springmsa.memberbff.user.me;

import com.springmsa.memberbff.user.UserService;
import com.springmsa.memberbff.user.dto.CurrentUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BffUserController {

    private final UserService userService;

    @GetMapping("/user/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication, request, response));
    }
}
