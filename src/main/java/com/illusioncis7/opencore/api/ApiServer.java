package com.illusioncis7.opencore.api;

import com.illusioncis7.opencore.rules.Rule;
import com.illusioncis7.opencore.rules.RuleService;
import com.illusioncis7.opencore.config.ConfigService;
import com.illusioncis7.opencore.config.ConfigParameter;
import com.illusioncis7.opencore.setup.SetupManager;
import com.illusioncis7.opencore.reputation.ReputationService;
import com.illusioncis7.opencore.reputation.ReputationService.PlayerReputation;
import com.illusioncis7.opencore.reputation.ChatReputationFlagService;
import com.illusioncis7.opencore.reputation.ReputationFlag;
import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
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
    private final ChatReputationFlagService chatFlagService;
    private final RuleService ruleService;
    private final ConfigService configService;
    private final SetupManager setupManager;
    private final Logger logger;
    private HttpServer server;
    private final boolean exposeReputations;

    public ApiServer(int port, boolean exposeReputations, VotingService votingService, ReputationService reputationService,
                     ChatReputationFlagService chatFlagService,
                     RuleService ruleService, ConfigService configService, SetupManager setupManager, Logger logger) throws IOException {
        this.votingService = votingService;
        this.reputationService = reputationService;
        this.chatFlagService = chatFlagService;
        this.ruleService = ruleService;
        this.configService = configService;
        this.setupManager = setupManager;
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
        server.createContext("/api/chatflags", exchange -> handle(exchange, this::writeChatFlags));

        server.createContext("/setup/status", this::handleSetupStatus);
        server.createContext("/setup/rules", this::handleRulesGet);
        server.createContext("/setup/rules/add", this::handleAddRule);
        server.createContext("/setup/configs", this::handleConfigsGet);
        server.createContext("/setup/configs/update", this::handleUpdateConfig);
        server.createContext("/setup/complete", this::handleComplete);

        server.createContext("/webpanel", this::handleStaticFiles);
        server.createContext("/webpanel/", this::handleStaticFiles);
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

    private JSONObject writeChatFlags() {
        JSONArray arr = new JSONArray();
        for (ReputationFlag f : chatFlagService.getActiveFlags()) {
            JSONObject o = new JSONObject();
            o.put("code", f.code);
            o.put("description", f.description);
            o.put("min", f.minChange);
            o.put("max", f.maxChange);
            arr.put(o);
        }
        return new JSONObject().put("flags", arr);
    }

    /* ===== Setup handlers ===== */
    private void handleSetupStatus(HttpExchange ex) throws IOException {
        if (!allowSetup(ex, false)) return;
        JSONObject obj = new JSONObject();
        obj.put("setupActive", setupManager.isSetupActive());
        writeJson(ex, obj);
    }

    private void handleRulesGet(HttpExchange ex) throws IOException {
        if (!allowSetup(ex, false)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        JSONArray arr = new JSONArray();
        for (Rule r : ruleService.getRules()) {
            JSONObject o = new JSONObject();
            o.put("id", r.id);
            o.put("text", r.text);
            o.put("category", r.category);
            arr.put(o);
        }
        writeJson(ex, new JSONObject().put("rules", arr));
    }

    private void handleAddRule(HttpExchange ex) throws IOException {
        if (!allowSetup(ex, true)) return;
        JSONObject req = readJson(ex);
        String text = req.optString("text", null);
        String cat = req.optString("category", "");
        if (text == null) { ex.sendResponseHeaders(400, -1); return; }
        Rule r = ruleService.addRule(text, cat);
        JSONObject resp = new JSONObject();
        resp.put("id", r != null ? r.id : -1);
        writeJson(ex, resp);
    }

    private void handleConfigsGet(HttpExchange ex) throws IOException {
        if (!allowSetup(ex, false)) return;
        JSONArray arr = new JSONArray();
        for (ConfigParameter p : configService.listParameters()) {
            JSONObject o = new JSONObject();
            o.put("id", p.getId());
            o.put("name", p.getParameterPath());
            o.put("type", p.getValueType().name());
            o.put("value", p.getCurrentValue());
            o.put("editable", p.isEditable());
            o.put("min", p.getMinValue());
            o.put("max", p.getMaxValue());
            o.put("impact", p.getImpactRating());
            arr.put(o);
        }
        writeJson(ex, new JSONObject().put("parameters", arr));
    }

    private void handleUpdateConfig(HttpExchange ex) throws IOException {
        if (!allowSetup(ex, true)) return;
        JSONObject req = readJson(ex);
        int id = req.optInt("id", -1);
        String value = req.optString("value", null);
        if (id <= 0 || value == null) { ex.sendResponseHeaders(400, -1); return; }
        boolean ok = configService.updateParameter(id, value, null);
        writeJson(ex, new JSONObject().put("success", ok));
    }

    private void handleComplete(HttpExchange ex) throws IOException {
        if (!allowSetup(ex, true)) return;
        setupManager.deactivate();
        writeJson(ex, new JSONObject().put("success", true));
    }

    private void handleStaticFiles(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath().substring("/webpanel".length());
        if (path.isEmpty() || "/".equals(path)) {
            path = "/index.html";
        }
        java.nio.file.Path file = setupManager.getWebDirectory().toPath().resolve(path.substring(1));
        if (!java.nio.file.Files.exists(file)) {
            ex.sendResponseHeaders(404, -1);
            return;
        }
        String mime = path.endsWith(".html") ? "text/html" : "text/plain";
        ex.getResponseHeaders().add("Content-Type", mime);
        byte[] data = java.nio.file.Files.readAllBytes(file);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private JSONObject readJson(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            byte[] data = is.readAllBytes();
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        }
    }

    private void writeJson(HttpExchange ex, JSONObject obj) throws IOException {
        byte[] data = obj.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private boolean allowSetup(HttpExchange ex, boolean post) throws IOException {
        if (!setupManager.isSetupActive()) { ex.sendResponseHeaders(403, -1); return false; }
        if (post && !"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return false; }
        if (!post && !"GET".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return false; }
        if (!ex.getRemoteAddress().getAddress().isLoopbackAddress()) { ex.sendResponseHeaders(403, -1); return false; }
        return true;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
