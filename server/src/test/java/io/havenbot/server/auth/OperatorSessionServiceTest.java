package io.havenbot.server.auth;

import io.havenbot.server.config.RuntimeProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorSessionServiceTest {
    private final OperatorSessionService service = new OperatorSessionService(
            new RuntimeProperties(
                    new RuntimeProperties.Operator("admin", "secret"),
                    new RuntimeProperties.Runtime("ws://127.0.0.1:8080/ws/bot", "ws://127.0.0.1:8080/ws/operator")
            )
    );

    @Test
    void loginCreatesValidSessionToken() {
        String token = service.login("admin", "secret");

        assertNotNull(token);
        assertTrue(service.isValid(token));
    }

    @Test
    void loginRejectsInvalidCredentials() {
        assertThrows(UnauthorizedException.class, () -> service.login("admin", "wrong"));
    }

    @Test
    void invalidateRemovesActiveToken() {
        String token = service.login("admin", "secret");

        service.invalidate(token);

        assertFalse(service.isValid(token));
    }
}
