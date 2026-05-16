package com.notify.agent.client.models.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FlatteningMapDeserializer extends JsonDeserializer<Map<String, Object>> {

    @Override
    public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        Map<String, Object> result = new HashMap<>();
        flatten("", node, result);
        return result;
    }

    private void flatten(String currentPath, JsonNode node, Map<String, Object> result) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> entry = iter.next();
                String newPath = currentPath.isEmpty() ? entry.getKey() : currentPath + "." + entry.getKey();
                flatten(newPath, entry.getValue(), result);
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                String newPath = currentPath + "[" + i + "]";
                flatten(newPath, arrayNode.get(i), result);
            }
        } else if (node.isValueNode()) {
            if (node.isTextual()) {
                result.put(currentPath, node.asText());
            } else if (node.isNumber()) {
                result.put(currentPath, node.numberValue());
            } else if (node.isBoolean()) {
                result.put(currentPath, node.asBoolean());
            } else if (node.isNull()) {
                result.put(currentPath, null);
            } else {
                result.put(currentPath, node.asText());
            }
        }
    }
}
