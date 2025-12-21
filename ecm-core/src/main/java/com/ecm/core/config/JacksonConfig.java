package com.ecm.core.config;

import com.fasterxml.jackson.core.Base64Variants;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer base64Customizer() {
        return builder -> builder.postConfigurer(
            mapper -> mapper.setBase64Variant(Base64Variants.MIME_NO_LINEFEEDS)
        );
    }
}
