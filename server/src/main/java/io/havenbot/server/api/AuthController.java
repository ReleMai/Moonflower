package io.havenbot.server.api;

import io.havenbot.server.auth.OperatorSessionService;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final OperatorSessionService operatorSessionService;

    public AuthController(OperatorSessionService operatorSessionService) {
        this.operatorSessionService = operatorSessionService;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        return Map.of("token", operatorSessionService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "X-Operator-Token", required = false) String token) {
        operatorSessionService.invalidate(token);
    }

    public record LoginRequest(String username, String password) {
    }
}
