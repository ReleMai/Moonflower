package io.havenbot.server.service;

public interface SecretProtectionService {
    String protect(String plaintext);

    String unprotect(String ciphertext);
}

