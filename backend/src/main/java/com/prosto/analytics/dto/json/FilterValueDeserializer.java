package com.prosto.analytics.dto.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.ArrayList;
import java.util.List;

public class FilterValueDeserializer extends StdDeserializer<List<String>> {

    public FilterValueDeserializer() {
        super(List.class);
    }

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = p.readValueAsTree();
        if (node.isArray()) {
            List<String> result = new ArrayList<>();
            for (JsonNode element : node) {
                result.add(element.asText());
            }
            return result;
        }
        return List.of(node.asText());
    }
}
