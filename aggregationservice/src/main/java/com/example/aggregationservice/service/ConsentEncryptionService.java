package com.example.aggregationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class ConsentEncryptionService {

    @Value("${encryption.secret-key}")
    private String secretKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    public String encrypt(String data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(getValidKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Encryption failed for data: {}", data, e);
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }

    public String decrypt(String encryptedData) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(getValidKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed for encrypted data: {}", encryptedData, e);
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }

    private byte[] getValidKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);

        // AES требует ключ длиной 16, 24 или 32 байта
        if (keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32) {
            return keyBytes;
        }

        // Если ключ невалидной длины - нормализуем до 16 байт
        byte[] normalizedKey = new byte[16];
        if (keyBytes.length < 16) {
            // Дополняем нулями
            System.arraycopy(keyBytes, 0, normalizedKey, 0, keyBytes.length);
        } else {
            // Обрезаем до 16 байт
            System.arraycopy(keyBytes, 0, normalizedKey, 0, 16);
        }

        log.warn("Key length was {} bytes, normalized to 16 bytes", keyBytes.length);
        return normalizedKey;
    }
}