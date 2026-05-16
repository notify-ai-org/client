package com.notify.agent.client.models;

/**
 * Request/response for token refresh with acp-server.
 */
public class TokenRefreshDto {

    public static class Request {
        private String clientId;
        private String refreshToken;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class Response {
        private String token;
        private long expiresInMs;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public long getExpiresInMs() { return expiresInMs; }
        public void setExpiresInMs(long expiresInMs) { this.expiresInMs = expiresInMs; }
    }
}
