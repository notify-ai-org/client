package com.notify.agent.auth;

import com.notify.agent.interfaces.TokenValidator;

import org.springframework.stereotype.Component;

/**
 * No-op {@link TokenValidator} for the client SDK.
 *
 * <p>
 * The client SDK does not maintain a token denylist — it trusts the JWT
 * signature and expiry claim for validity. This implementation always returns
 * {@code true}, effectively skipping the secondary store check.
 * </p>
 *
 * <p>
 * If you need active revocation on the client side (e.g. for multi-device
 * logout), replace this bean with an implementation backed by a shared store.
 * </p>
 */
@Component
public class NoOpTokenValidator implements TokenValidator {

    @Override
    public boolean isAccessTokenValid(String accessToken) {
        return true; // trust the JWT signature + expiry — no denylist on the client
    }

    @Override
    public boolean isRefreshTokenValid(String refreshToken) {
        return true;
    }
}
