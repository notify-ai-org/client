package com.notify.agent;

import com.notify.agent.interfaces.TokenStore;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds access token, refresh token, and expiry for the client SDK.
 * Thread-safe. Implements {@link TokenStore} so it can be injected into
 * {@code JwtService} without requiring any network or persistence dependency.
 *
 * <p>
 * {@link #invalidateTokens} clears the in-memory state; there is no
 * denylist — the SDK trusts JWT expiry for revocation.
 * </p>
 */
public class TokenHolder implements TokenStore {

    private final AtomicReference<String> token = new AtomicReference<>(null);
    private final AtomicReference<String> refreshToken = new AtomicReference<>(null);
    private final AtomicReference<String> headerToken = new AtomicReference<>(null);
    private volatile long expiresAtMs = 0;

    // -------------------------------------------------------------------------
    // TokenHolder-specific read API
    // -------------------------------------------------------------------------

    public String getToken() {
        return token.get();
    }

    public String getRefreshToken() {
        return refreshToken.get();
    }

    public String getHeaderToken() {
        return refreshToken.get();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMs;
    }

    public boolean hasToken() {
        return token.get() != null && !token.get().isEmpty();
    }

    public void setTokens(String accessToken, String refresh, String header,
            long expiresInMs) {
        token.set(accessToken);
        if (refresh != null)
            refreshToken.set(refresh);
        headerToken.set(header);
        this.expiresAtMs = expiresInMs > 0
                ? System.currentTimeMillis() + expiresInMs
                : Long.MAX_VALUE;
    }

    public void setToken(String accessToken, long expiresInMs) {
        token.set(accessToken);
        this.expiresAtMs = expiresInMs > 0
                ? System.currentTimeMillis() + expiresInMs
                : Long.MAX_VALUE;
    }

    // -------------------------------------------------------------------------
    // TokenStore implementation
    // -------------------------------------------------------------------------

    /**
     * Stores the access token in-memory.
     * TTL parameters are used to compute the local expiry; no external store is
     * involved.
     */
    @Override
    public void storeTokens(String userId, String accessToken, String refreshToken,
            String headerToken,
            long accessTtlSeconds, long refreshTtlSeconds) {
        setTokens(accessToken, refreshToken, headerToken, accessTtlSeconds * 1000);
    }

    /**
     * Clears the in-memory token state.
     * Token arguments are accepted for interface compatibility but are not stored.
     */
    @Override
    public void invalidateTokens(String accessToken, String refreshToken) {
        token.set(null);
        this.refreshToken.set(null);
        this.expiresAtMs = 0;
    }
}
