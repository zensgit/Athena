package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.oauth.OAuthCredentialService;
import com.ecm.core.integration.oauth.OAuthEnvironmentStatus;
import com.ecm.core.integration.oauth.OAuthReauthRequiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailOAuthService {

    private final OAuthCredentialService oauthCredentialService;

    public OAuthAuthorizeResponse buildAuthorizeUrl(UUID accountId, String callbackUrl, String redirectUrl) {
        OAuthCredentialService.OAuthAuthorizeResponse response = oauthCredentialService.buildAuthorizeUrl(
            MailOAuthCredentialOwnerAdapter.OWNER_TYPE,
            accountId,
            callbackUrl,
            redirectUrl
        );
        return new OAuthAuthorizeResponse(response.url(), response.state());
    }

    public OAuthCallbackResult handleCallback(String code, String state) {
        OAuthCredentialService.OAuthCallbackResult result = oauthCredentialService.handleCallback(code, state);
        if (!MailOAuthCredentialOwnerAdapter.OWNER_TYPE.equals(result.ownerType())) {
            throw new IllegalStateException("Unexpected OAuth callback owner type: " + result.ownerType());
        }
        return new OAuthCallbackResult(result.ownerId(), result.redirectUrl());
    }

    public String resolveAccessToken(MailAccount account) {
        try {
            return oauthCredentialService.resolveAccessToken(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, account.getId());
        } catch (OAuthReauthRequiredException ex) {
            throw new MailOAuthReauthRequiredException(account.getId(), ex.getError(), ex.getErrorDescription());
        }
    }

    public MailFetcherService.OAuthEnvCheckResult checkOAuthEnv(UUID accountId) {
        return toMailEnvCheck(
            oauthCredentialService.checkEnvironment(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId)
        );
    }

    public MailFetcherService.OAuthEnvCheckResult checkOAuthEnv(MailAccount account) {
        return checkOAuthEnv(account.getId());
    }

    public void clearStoredTokens(UUID accountId) {
        oauthCredentialService.clearTokens(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId);
    }

    public void evictSession(UUID accountId) {
        oauthCredentialService.evictSession(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId);
    }

    private MailFetcherService.OAuthEnvCheckResult toMailEnvCheck(OAuthEnvironmentStatus status) {
        return new MailFetcherService.OAuthEnvCheckResult(
            status.oauthAccount(),
            status.configured(),
            status.credentialKey(),
            status.missingEnvKeys()
        );
    }

    public record OAuthAuthorizeResponse(String url, String state) {
    }

    public record OAuthCallbackResult(UUID accountId, String redirectUrl) {
    }
}
