package io.havenbot.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SecurityStartupVerifier implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SecurityStartupVerifier.class);

    private final RuntimeProperties runtimeProperties;
    private final String serverAddress;

    public SecurityStartupVerifier(RuntimeProperties runtimeProperties,
                                   @Value("${server.address:0.0.0.0}") String serverAddress) {
        this.runtimeProperties = runtimeProperties;
        this.serverAddress = serverAddress;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("changeme".equals(runtimeProperties.operator().password())) {
            log.warn("Operator password is still the default value 'changeme'. Change HAVEN_OPERATOR_PASSWORD before exposing this dashboard beyond your own desktop.");
        }
        if (!isLoopback(serverAddress)) {
            log.warn("Server is bound to non-loopback address {}. Anyone who can reach this port and knows valid credentials can access the dashboard.", serverAddress);
        } else {
            log.info("Server is bound to loopback address {}. Remote machines cannot connect unless you explicitly rebind or tunnel the port.", serverAddress);
        }
    }

    private boolean isLoopback(String address) {
        return "127.0.0.1".equals(address)
                || "::1".equals(address)
                || "localhost".equalsIgnoreCase(address);
    }
}
