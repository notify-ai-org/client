package com.notify.agent.client.models;

/**
 * Request/response DTOs for client registration with acp-server.
 */
public class ClientRegistrationDto {

    public static class Request {
        private String clientId;
        private String applicationName;
        private String basePackage;
        private String rawToken;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        public String getBasePackage() {
            return basePackage;
        }

        public void setBasePackage(String basePackage) {
            this.basePackage = basePackage;
        }

        public String getRawToken() {
            return rawToken;
        }

        public void setRawToken(String rawToken) {
            this.rawToken = rawToken;
        }
    }

    public static class Response {
        private String clientId;
        private String token;
        private String apiKey;
        private String apiSecret;
        private String refreshToken;
        private String kafkaHeaderToken;
        private long expiresInMs;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        /**
         * @return the apiKey
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * @param apiKey the apiKey to set
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * @return the apiSecret
         */
        public String getApiSecret() {
            return apiSecret;
        }

        /**
         * @param apiSecret the apiSecret to set
         */
        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public long getExpiresInMs() {
            return expiresInMs;
        }

        public void setExpiresInMs(long expiresInMs) {
            this.expiresInMs = expiresInMs;
        }

        public String getKafkaHeaderToken() {
            return kafkaHeaderToken;
        }

        public void setKafkaHeaderToken(String kafkaHeaderToken) {
            this.kafkaHeaderToken = kafkaHeaderToken;
        }
    }
}
