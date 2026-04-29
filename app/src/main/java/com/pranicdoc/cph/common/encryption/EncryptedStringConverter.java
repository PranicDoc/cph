package com.pranicdoc.cph.common.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts String fields
 * marked with @Convert(converter = EncryptedStringConverter.class).
 *
 * The AES key is loaded from application config (should point to Vault in prod).
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesUtil aesUtil;

    public EncryptedStringConverter(
            @Value("${app.encryption.key:MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=}") String base64Key) {
        // Decode the Base64-encoded 32-byte key
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.aesUtil = new AesUtil(keyBytes);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return aesUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return aesUtil.decrypt(dbData);
    }
}
