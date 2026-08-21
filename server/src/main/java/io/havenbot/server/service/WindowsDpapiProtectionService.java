package io.havenbot.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class WindowsDpapiProtectionService implements SecretProtectionService {
    private static final Logger log = LoggerFactory.getLogger(WindowsDpapiProtectionService.class);
    private static final String PREFIX_DPAPI = "dpapi:";
    private static final String PREFIX_B64 = "b64:";

    @Override
    public String protect(String plaintext) {
        if (plaintext == null) {
            return "";
        }
        if (isWindows()) {
            try {
                return PREFIX_DPAPI + execPowerShell(plaintext, true);
            } catch (RuntimeException ex) {
                log.warn("DPAPI protect failed, falling back to local base64 storage: {}", ex.getMessage());
            }
        }
        return PREFIX_B64 + encodeBase64(plaintext);
    }

    @Override
    public String unprotect(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }
        if (ciphertext.startsWith(PREFIX_DPAPI)) {
            return execPowerShell(ciphertext.substring(PREFIX_DPAPI.length()), false);
        }
        if (ciphertext.startsWith(PREFIX_B64)) {
            return decodeBase64(ciphertext.substring(PREFIX_B64.length()));
        }
        if (isWindows()) {
            try {
                return execPowerShell(ciphertext, false);
            } catch (RuntimeException ex) {
                log.warn("Legacy DPAPI unprotect failed, attempting base64 fallback: {}", ex.getMessage());
            }
        }
        return decodeBase64(ciphertext);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private String execPowerShell(String value, boolean protect) {
        String script;
        if (protect) {
            script = "$bytes=[Text.Encoding]::UTF8.GetBytes($env:HAVENBOT_SECRET);" +
                    "$data=[Security.Cryptography.ProtectedData]::Protect($bytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);" +
                    "[Convert]::ToBase64String($data)";
        } else {
            script = "$bytes=[Convert]::FromBase64String($env:HAVENBOT_SECRET);" +
                    "$data=[Security.Cryptography.ProtectedData]::Unprotect($bytes,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);" +
                    "[Text.Encoding]::UTF8.GetString($data)";
        }
        ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-Command", script);
        builder.environment().put("HAVENBOT_SECRET", value);
        try {
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.isEmpty()) {
                        output.append(line);
                    } else {
                        output.append(System.lineSeparator()).append(line);
                    }
                }
                StringBuilder errors = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    if (errors.isEmpty()) {
                        errors.append(line);
                    } else {
                        errors.append(System.lineSeparator()).append(line);
                    }
                }
                int exit = process.waitFor();
                if (exit != 0 || output.isEmpty()) {
                    String detail = errors.isEmpty() ? "no stderr" : errors.toString();
                    throw new IllegalStateException("DPAPI process failed: " + detail);
                }
                return output.toString().trim();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to protect secret.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to protect secret.", ex);
        }
    }

    private String encodeBase64(String plaintext) {
        return Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeBase64(String ciphertext) {
        return new String(Base64.getDecoder().decode(ciphertext), StandardCharsets.UTF_8);
    }
}
