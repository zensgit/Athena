package com.ecm.core.security.secret;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ecm.security.secret")
public class SecretCryptoProperties {

    private boolean enabled = false;

    private String activeKeyVersion = "v1";

    private Map<String, String> keys = new LinkedHashMap<>();
}
