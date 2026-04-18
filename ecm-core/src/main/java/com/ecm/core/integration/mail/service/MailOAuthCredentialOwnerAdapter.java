package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.oauth.OAuthCredentialOwner;
import com.ecm.core.integration.oauth.OAuthCredentialOwnerAdapter;
import com.ecm.core.integration.oauth.OAuthProviderType;
import com.ecm.core.integration.oauth.model.OAuthCredential;
import com.ecm.core.integration.oauth.repository.OAuthCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MailOAuthCredentialOwnerAdapter implements OAuthCredentialOwnerAdapter {

    public static final String OWNER_TYPE = "MAIL_ACCOUNT";
    private static final String MAIL_ENV_PREFIX = "ECM_MAIL_OAUTH_";

    private final MailAccountRepository accountRepository;
    private final OAuthCredentialRepository oauthCredentialRepository;

    @Override
    public String ownerType() {
        return OWNER_TYPE;
    }

    @Override
    public OAuthCredentialOwner loadOwner(UUID ownerId) {
        MailAccount account = loadAccount(ownerId);
        return oauthCredentialRepository.findByOwnerTypeAndOwnerId(OWNER_TYPE, ownerId)
            .map(credential -> toOwner(account, credential))
            .orElseGet(() -> toOwner(account));
    }

    @Override
    public OAuthCredentialOwner saveTokens(UUID ownerId, String accessToken, String refreshToken, LocalDateTime expiresAt) {
        MailAccount account = loadAccount(ownerId);
        account.setOauthAccessToken(accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            account.setOauthRefreshToken(refreshToken);
        }
        account.setOauthTokenExpiresAt(expiresAt);
        MailAccount savedAccount = accountRepository.save(account);
        OAuthCredential savedCredential = saveCredential(
            savedAccount,
            accessToken,
            savedAccount.getOauthRefreshToken(),
            expiresAt
        );
        return toOwner(savedAccount, savedCredential);
    }

    @Override
    public OAuthCredentialOwner clearTokens(UUID ownerId) {
        MailAccount account = loadAccount(ownerId);
        account.setOauthAccessToken(null);
        account.setOauthRefreshToken(null);
        account.setOauthTokenExpiresAt(null);
        MailAccount savedAccount = accountRepository.save(account);
        OAuthCredential credential = oauthCredentialRepository.findByOwnerTypeAndOwnerId(OWNER_TYPE, ownerId)
            .map(existing -> {
                existing.setAccessToken(null);
                existing.setRefreshToken(null);
                existing.setTokenExpiresAt(null);
                return oauthCredentialRepository.save(existing);
            })
            .orElseGet(() -> saveCredential(savedAccount, null, null, null));
        return toOwner(savedAccount, credential);
    }

    public void syncAccount(MailAccount account) {
        if (account.getId() == null) {
            throw new IllegalArgumentException("Mail account must be persisted before OAuth credential sync");
        }
        if (account.getSecurity() != MailAccount.SecurityType.OAUTH2) {
            oauthCredentialRepository.deleteByOwnerTypeAndOwnerId(OWNER_TYPE, account.getId());
            return;
        }
        saveCredential(account, account.getOauthAccessToken(), account.getOauthRefreshToken(), account.getOauthTokenExpiresAt());
    }

    public void deleteCredential(UUID ownerId) {
        oauthCredentialRepository.deleteByOwnerTypeAndOwnerId(OWNER_TYPE, ownerId);
    }

    @Override
    public String buildCredentialEnvKey(String normalizedCredentialKey, String suffix) {
        return MAIL_ENV_PREFIX + normalizedCredentialKey + "_" + suffix;
    }

    @Override
    public String buildProviderEnvKey(OAuthProviderType provider, String suffix) {
        return switch (provider) {
            case GOOGLE -> MAIL_ENV_PREFIX + "GOOGLE_" + suffix;
            case MICROSOFT -> MAIL_ENV_PREFIX + "MICROSOFT_" + suffix;
            case CUSTOM -> throw new IllegalStateException("Custom OAuth provider requires credential key");
        };
    }

    private MailAccount loadAccount(UUID ownerId) {
        MailAccount account = accountRepository.findById(ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Mail account not found: " + ownerId));
        if (account.getSecurity() != MailAccount.SecurityType.OAUTH2) {
            throw new IllegalArgumentException("Mail account is not configured for OAuth2");
        }
        return account;
    }

    private OAuthCredential saveCredential(
        MailAccount account,
        String accessToken,
        String refreshToken,
        LocalDateTime expiresAt
    ) {
        OAuthCredential credential = oauthCredentialRepository.findByOwnerTypeAndOwnerId(OWNER_TYPE, account.getId())
            .orElseGet(OAuthCredential::new);
        credential.setOwnerType(OWNER_TYPE);
        credential.setOwnerId(account.getId());
        credential.setProvider(mapProvider(account.getOauthProvider()));
        credential.setTokenEndpoint(account.getOauthTokenEndpoint());
        credential.setTenantId(account.getOauthTenantId());
        credential.setScope(account.getOauthScope());
        credential.setCredentialKey(account.getOauthCredentialKey());
        credential.setAccessToken(accessToken);
        credential.setRefreshToken(refreshToken);
        credential.setTokenExpiresAt(expiresAt);
        return oauthCredentialRepository.save(credential);
    }

    private OAuthCredentialOwner toOwner(MailAccount account) {
        return new OAuthCredentialOwner(
            OWNER_TYPE,
            account.getId(),
            account.getName(),
            mapProvider(account.getOauthProvider()),
            account.getOauthTokenEndpoint(),
            account.getOauthTenantId(),
            account.getOauthScope(),
            account.getOauthCredentialKey(),
            account.getOauthAccessToken(),
            account.getOauthRefreshToken(),
            account.getOauthTokenExpiresAt()
        );
    }

    private OAuthCredentialOwner toOwner(MailAccount account, OAuthCredential credential) {
        return new OAuthCredentialOwner(
            OWNER_TYPE,
            account.getId(),
            account.getName(),
            credential.getProvider(),
            coalesce(credential.getTokenEndpoint(), account.getOauthTokenEndpoint()),
            coalesce(credential.getTenantId(), account.getOauthTenantId()),
            coalesce(credential.getScope(), account.getOauthScope()),
            coalesce(credential.getCredentialKey(), account.getOauthCredentialKey()),
            coalesce(credential.getAccessToken(), account.getOauthAccessToken()),
            coalesce(credential.getRefreshToken(), account.getOauthRefreshToken()),
            credential.getTokenExpiresAt() != null ? credential.getTokenExpiresAt() : account.getOauthTokenExpiresAt()
        );
    }

    private String coalesce(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private OAuthProviderType mapProvider(MailAccount.OAuthProvider provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case GOOGLE -> OAuthProviderType.GOOGLE;
            case MICROSOFT -> OAuthProviderType.MICROSOFT;
            case CUSTOM -> OAuthProviderType.CUSTOM;
        };
    }
}
