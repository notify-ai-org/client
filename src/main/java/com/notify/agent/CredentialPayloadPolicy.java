package com.notify.agent;
import com.fasterxml.jackson.databind.*;
import java.util.*;
/** Reject credential-bearing fields before notification persistence or transport. Does not inspect message prose. */
public final class CredentialPayloadPolicy {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Set<String> KEYS=Set.of("credentials","secret","signingsecret","callbacksecret","password","apikey","apisecret","authtoken","accesstoken","refreshtoken","authorization","privatekey","serviceaccountjson","secretstring","secretbinary","awssecretarn");
    private CredentialPayloadPolicy() {}
    public static void validate(Object data) {
        try { visit(JSON.valueToTree(data),0); }
        catch(Exception e) { throw new IllegalArgumentException("Credentials are not allowed in notification payloads"); }
    }
    public static void validateJson(String data) {
        if (data==null || data.isBlank()) return;
        try { visit(JSON.readTree(data),0); }
        catch(Exception e) { throw new IllegalArgumentException("Credentials are not allowed in notification payloads"); }
    }
    private static void visit(JsonNode node,int depth) {
        if (node==null) return;
        if (depth>50) throw new IllegalArgumentException();
        if (node.isObject()) {
            var it=node.fields();
            while(it.hasNext()) {
                var e=it.next();
                String compact=e.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                if (KEYS.stream().anyMatch(k -> compact.equals(k) || compact.endsWith(k))) throw new IllegalArgumentException();
                visit(e.getValue(),depth+1);
            }
        } else if (node.isArray()) { for(var child:node) visit(child,depth+1); }
        else if (node.isTextual()) {
            String text=node.asText().trim();
            if (text.contains("-----BEGIN PRIVATE KEY-----") || text.contains("-----BEGIN RSA PRIVATE KEY-----")) throw new IllegalArgumentException();
            if (text.startsWith("{") || text.startsWith("[")) {
                try { JsonNode nested=JSON.readTree(text); visit(nested,depth+1); }
                catch(com.fasterxml.jackson.core.JsonProcessingException ignored) { /* ordinary prose */ }
            }
        }
    }
}
