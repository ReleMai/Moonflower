package io.havenbot.server.auth;

import io.havenbot.server.config.RuntimeProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OperatorSessionService {
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final RuntimeProperties properties;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public OperatorSessionService(RuntimeProperties properties) {
        this.properties = properties;
    }

    public String login(String username, String password) {
        if (!properties.operator().username().equals(username) || !properties.operator().password().equals(password)) {
            throw new UnauthorizedException("Invalid operator credentials.");
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, Instant.now().plus(TOKEN_TTL));
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant expiry = sessions.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}

