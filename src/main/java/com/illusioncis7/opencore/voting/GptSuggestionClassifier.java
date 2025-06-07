package com.illusioncis7.opencore.voting;

import com.illusioncis7.opencore.database.Database;
import com.illusioncis7.opencore.gpt.GptService;
import com.illusioncis7.opencore.rules.RuleService;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Uses the central GPT service to classify suggestions by type.
 */
public class GptSuggestionClassifier {

    private final GptService gptService;
    private final Database database;
    private final Logger logger;
    private final RuleService ruleService;

    public GptSuggestionClassifier(GptService gptService, Database database, RuleService ruleService, Logger logger) {
        this.gptService = gptService;
        this.database = database;
        this.ruleService = ruleService;
        this.logger = logger;
    }

    /**
     * Classify the suggestion text and store the result in the database.
     *
     * @param suggestionId database id of the suggestion
     * @param text         original suggestion text
     * @param onConfig     callback executed if the suggestion is classified as CONFIG_CHANGE
     * @param onRule       callback executed if the suggestion is classified as RULE_CHANGE
     */
    public void classify(int suggestionId, String text, Runnable onConfig, Runnable onRule,
                         java.util.function.Consumer<SuggestionType> after) {
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("s", text);
        vars.put("rules", joinRules());
        gptService.submitPolicyRequest("suggest_classify", vars, null, response -> {
            if (response == null || response.isEmpty()) {
                handleFailure(suggestionId, "Empty GPT response");
                return;
            }
            if (!com.illusioncis7.opencore.gpt.GptSchemas.validate("suggest_classify", response)) {
                handleFailure(suggestionId, "Invalid schema");
                return;
            }
            try {
                JSONObject obj = new JSONObject(response);
                String typeStr = obj.getString("suggestion_type");
                SuggestionType type = SuggestionType.valueOf(typeStr);
                String reasoning = obj.optString("reasoning", "");
                double confidence = obj.optDouble("confidence", 0.0);

                logger.info("GPT Klassifikation: " + type + " (" + confidence + ") – Grund: " + reasoning);
                updateSuggestion(suggestionId, type, reasoning, confidence);

                if (type == SuggestionType.CONFIG_CHANGE && onConfig != null) {
                    onConfig.run();
                } else if (type == SuggestionType.RULE_CHANGE && onRule != null) {
                    onRule.run();
                }
                if (after != null) {
                    after.accept(type);
                }
            } catch (Exception e) {
                handleFailure(suggestionId, "Parse error: " + e.getMessage());
            }
        });
    }

    private void updateSuggestion(int id, SuggestionType type, String reasoning, double confidence) {
        if (database.getConnection() == null) {
            return;
        }
        String sql = "UPDATE suggestions SET suggestion_type = ?, gpt_reasoning = ?, gpt_confidence = ?, classified_at = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type.name());
            ps.setString(2, reasoning);
            ps.setDouble(3, confidence);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setInt(5, id);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("Failed to store GPT classification: " + e.getMessage());
        }
    }

    private void handleFailure(int id, String error) {
        logger.warning("GPT classification failed for suggestion " + id + ": " + error);
        if (database.getConnection() == null) {
            return;
        }
        String sql = "UPDATE suggestions SET gpt_reasoning = ?, classified_at = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, error);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warning("Failed to log classification error: " + e.getMessage());
        }
    }

    private String joinRules() {
        StringBuilder sb = new StringBuilder();
        for (com.illusioncis7.opencore.rules.Rule r : ruleService.getRules()) {
            sb.append(r.text).append("\n");
        }
        return sb.toString();
    }
}
