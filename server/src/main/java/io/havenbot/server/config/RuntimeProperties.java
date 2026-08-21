package io.havenbot.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "havenbot")
public record RuntimeProperties(
        Operator operator,
        Runtime runtime
) {
    public record Operator(String username, String password) {
    }

    public record Runtime(String botServerUrl, String operatorWebUrl) {
    }
}

