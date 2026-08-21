package io.havenbot.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class OperatorAuthInterceptor implements HandlerInterceptor {
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/health"
    );

    private final OperatorSessionService sessionService;

    public OperatorAuthInterceptor(OperatorSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || PUBLIC_PATHS.contains(path)) {
            return true;
        }
        String token = request.getHeader("X-Operator-Token");
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        if (!sessionService.isValid(token)) {
            throw new UnauthorizedException("Missing or invalid operator token.");
        }
        return true;
    }
}
