package com.ecm.core.security.mfa;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    @Test
    void verifyCodeAcceptsCurrentWindowCode() throws Exception {
        TotpService service = new TotpService();
        String secret = service.generateSecret();
        long window = Instant.now().getEpochSecond() / 30;

        Method method = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        method.setAccessible(true);
        String code = (String) method.invoke(service, secret, window);

        assertTrue(service.verifyCode(secret, code));
    }

    @Test
    void verifyCodeRejectsInvalidCode() {
        TotpService service = new TotpService();
        String secret = service.generateSecret();
        assertFalse(service.verifyCode(secret, "123"));
        assertFalse(service.verifyCode(secret, "abcdef"));
    }
}
