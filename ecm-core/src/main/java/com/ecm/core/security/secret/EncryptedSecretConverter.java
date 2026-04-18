package com.ecm.core.security.secret;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedSecretConverter implements AttributeConverter<String, String> {

    private static volatile SecretCryptoService secretCryptoService;

    static void setSecretCryptoService(SecretCryptoService service) {
        secretCryptoService = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        SecretCryptoService service = secretCryptoService;
        return service != null ? service.protect(attribute) : attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        SecretCryptoService service = secretCryptoService;
        return service != null ? service.reveal(dbData) : dbData;
    }
}
