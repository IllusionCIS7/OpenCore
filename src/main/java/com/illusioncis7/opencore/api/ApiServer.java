package com.illusioncis7.opencore.api;

import com.illusioncis7.opencore.rules.Rule;
import com.illusioncis7.opencore.rules.RuleService;
import com.illusioncis7.opencore.reputation.ReputationService;
import com.illusioncis7.opencore.reputation.ReputationService.PlayerReputation;
import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;

/**
 * Lightweight HTTP server exposing JSON endpoints for rules, suggestions and reputations.
 */
public class ApiServer {
    private final VotingService votingService;
    private final ReputationService reputationService;
    private final RuleService ruleService;
    private final Logger logger;
    private HttpServer server;
    private final boolean exposeReputations;

    public ApiServer(int port, boolean exposeReputations, VotingService votingService, ReputationService reputationService,
                     RuleService ruleService, Logger logger) throws IOException {
        this.votingService = votingService;
        this.reputationService = reputationService;
        this.ruleService = ruleService;
        this.logger = logger;
        this.exposeReputations = exposeReputations;
        if (port > 0) {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            registerContexts();
            server.start();
            logger.info("API server listening on port " + port);
        } else {
            logger.warning("API server disabled due to invalid port: " + port);
        }
    }

    private void registerContexts() {
        server.createContext("/api/suggestions", exchange -> handle(exchange, this::writeSuggestions));
        server.createContext("/api/rules", exchange -> handle(exchange, this::writeRules));
        if (exposeReputations) {
            server.createContext("/api/reputations", exchange -> handle(exchange, this::writeReputations));
        }
    }

    private interface JsonWriter {
        JSONObject build();
    }

    private void handle(HttpExchange exchange, JsonWriter writer) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        JSONObject obj = writer.build();
        byte[] data = obj.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private JSONObject writeSuggestions() {
        JSONArray arr = new JSONArray();
        List<Suggestion> list = votingService.getOpenSuggestions();
        for (Suggestion s : list) {
            JSONObject o = new JSONObject();
            o.put("id", s.id);
            o.put("text", s.text);
            if (s.description != null) {
                o.put("description", s.description);
            }
            o.put("player", s.playerUuid.toString());
            arr.put(o);
        }
        return new JSONObject().put("suggestions", arr);
    }

    private JSONObject writeRules() {
        JSONArray arr = new JSONArray();
        List<Rule> list = ruleService.getRules();
        for (Rule r : list) {
            JSONObject o = new JSONObject();
            o.put("id", r.id);
            o.put("text", r.text);
            o.put("category", r.category);
            arr.put(o);
        }
        return new JSONObject().put("rules", arr);
    }

    private JSONObject writeReputations() {
        JSONArray arr = new JSONArray();
        List<PlayerReputation> list = reputationService.listReputations();
        for (PlayerReputation p : list) {
            JSONObject o = new JSONObject();
            o.put("uuid", p.uuid.toString());
            o.put("score", p.score);
            arr.put(o);
        }
        return new JSONObject().put("reputations", arr);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
