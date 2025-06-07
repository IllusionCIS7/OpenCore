package com.illusioncis7.opencore.gpt;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Map;

/** Utility for validating GPT responses against predefined JSON schemas. */
public final class GptSchemas {
    private static final Map<String, Schema> SCHEMAS = new HashMap<>();

    static {
        // Schema for suggestion_classifier responses
        SCHEMAS.put("suggest_classify", load("""
            {
              "type": "object",
              "properties": {
                "suggestion_type": {"type": "string"},
                "reasoning": {"type": "string"},
                "confidence": {"type": "number"}
              },
              "required": ["suggestion_type"],
              "additionalProperties": true
            }
        """));
        // Schema for suggest_map responses
        SCHEMAS.put("suggest_map", load("""
            {
              "type": "object",
              "properties": {
                "id": {"type": "integer"},
                "value": {"type": "string"}
              },
              "required": ["id", "value"],
              "additionalProperties": true
            }
        """));
        // Schema for rule_map responses
        SCHEMAS.put("rule_map", load("""
            {
              "type": "object",
              "properties": {
                "id": {"type": "integer"},
                "text": {"type": "string"},
                "summary": {"type": "string"},
                "impact": {"type": "integer"}
              },
              "required": ["id", "text"],
              "additionalProperties": true
            }
        """));
        // Schema for chat_analysis responses
        SCHEMAS.put("chat_analysis", load("""
            {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "alias_id": {"type": "string"},
                  "reason_summary": {"type": "string"}
                },
                "required": ["alias_id"],
                "additionalProperties": true
              }
            }
        """));
    }

    private static Schema load(String schemaJson) {
        JSONObject obj = new JSONObject(new JSONTokener(schemaJson));
        return SchemaLoader.load(obj);
    }

    private GptSchemas() {
    }

    /**
     * Validate the given response against the schema for the specified template.
     *
     * @param template name of the GPT template
     * @param response response string
     * @return {@code true} if valid or no schema defined
     */
    public static boolean validate(String template, String response) {
        Schema schema = SCHEMAS.get(template);
        if (schema == null) {
            return true;
        }
        try {
            Object json;
            String trimmed = response.trim();
            if (trimmed.startsWith("{")) {
                json = new JSONObject(trimmed);
            } else if (trimmed.startsWith("[")) {
                json = new JSONArray(trimmed);
            } else {
                return false;
            }
            schema.validate(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
