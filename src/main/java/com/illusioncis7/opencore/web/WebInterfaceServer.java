package com.illusioncis7.opencore.web;

import com.illusioncis7.opencore.voting.Suggestion;
import com.illusioncis7.opencore.voting.VotingService;
import com.illusioncis7.opencore.web.SuggestionCommentService.Comment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Lightweight web API for suggestions and voting.
 */
public class WebInterfaceServer {
    private final WebTokenService tokenService;
    private final VotingService votingService;
    private final SuggestionCommentService commentService;
    private final Logger logger;
    private HttpServer server;

    public WebInterfaceServer(WebTokenService tokenService, VotingService votingService,
                              SuggestionCommentService commentService, Logger logger) throws IOException {
        this.tokenService = tokenService;
        this.votingService = votingService;
        this.commentService = commentService;
        this.logger = logger;
        server = HttpServer.create(new InetSocketAddress(tokenService.getInternalHost(), tokenService.getInternalPort()), 0);
        registerContexts();
        server.start();
        logger.info("Web interface listening on " + tokenService.getInternalHost() + ":" + tokenService.getInternalPort());
    }

    private void registerContexts() {
        server.createContext("/validate-token", this::handleValidateToken);
        server.createContext("/suggestions", this::handleSuggestions);
        server.createContext("/submit-suggestion", this::handleSubmitSuggestion);
        server.createContext("/cast-vote", this::handleCastVote);
        server.createContext("/suggestion-comments", this::handleComments);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handleValidateToken(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        JSONObject req = readJson(ex);
        String token = req.optString("token", null);
        String type = req.optString("type", null);
        UUID player = tokenService.validateToken(token, type);
        JSONObject resp = new JSONObject();
        resp.put("valid", player != null);
        if (player != null) resp.put("player", player.toString());
        writeJson(ex, resp);
    }

    private void handleSuggestions(HttpExchange ex) throws IOException {
        String token = getParam(ex, "token");
        if (tokenService.checkToken(token) == null) { ex.sendResponseHeaders(403, -1); return; }
        JSONArray arr = new JSONArray();
        for (Suggestion s : votingService.getOpenSuggestions()) {
            VotingService.VoteWeights w = votingService.getVoteWeights(s.id);
            JSONObject o = new JSONObject();
            o.put("id", s.id);
            o.put("text", s.text);
            if (s.description != null) o.put("description", s.description);
            o.put("player", s.playerUuid.toString());
            o.put("remaining", votingService.getRemainingMinutes(s.created));
            o.put("yes", w.yesWeight);
            o.put("no", w.noWeight);
            o.put("required", w.requiredWeight);
            o.put("expired", false);
            arr.put(o);
        }
        for (Suggestion s : votingService.getClosedSuggestions()) {
            if (!s.expired) continue;
            VotingService.VoteWeights w = votingService.getVoteWeights(s.id);
            JSONObject o = new JSONObject();
            o.put("id", s.id);
            o.put("text", s.text);
            if (s.description != null) o.put("description", s.description);
            o.put("player", s.playerUuid.toString());
            o.put("remaining", 0);
            o.put("yes", w.yesWeight);
            o.put("no", w.noWeight);
            o.put("required", w.requiredWeight);
            o.put("expired", true);
            arr.put(o);
        }
        writeJson(ex, new JSONObject().put("suggestions", arr));
    }

    private void handleSubmitSuggestion(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        JSONObject req = readJson(ex);
        String token = req.optString("token", null);
        UUID player = tokenService.checkToken(token);
        if (player == null) { ex.sendResponseHeaders(403, -1); return; }
        int paramId = req.optInt("parameter", -1);
        String value = req.optString("value", null);
        String reason = req.optString("reason", "");
        if (paramId <= 0 || value == null || reason.isEmpty()) { ex.sendResponseHeaders(400, -1); return; }
        int id = votingService.submitDirectSuggestion(player, paramId, value, reason);
        JSONObject resp = new JSONObject();
        resp.put("id", id);
        writeJson(ex, resp);
    }

    private void handleCastVote(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }
        JSONObject req = readJson(ex);
        String token = req.optString("token", null);
        UUID player = tokenService.checkToken(token);
        if (player == null) { ex.sendResponseHeaders(403, -1); return; }
        int suggestion = req.optInt("suggestion", -1);
        String vote = req.optString("vote", "");
        boolean ok = true;
        if ("yes".equalsIgnoreCase(vote)) ok = votingService.castVote(player, suggestion, true);
        else if ("no".equalsIgnoreCase(vote)) ok = votingService.castVote(player, suggestion, false);
        JSONObject resp = new JSONObject();
        resp.put("success", ok);
        writeJson(ex, resp);
    }

    private void handleComments(HttpExchange ex) throws IOException {
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            String token = getParam(ex, "token");
            if (tokenService.checkToken(token) == null) { ex.sendResponseHeaders(403, -1); return; }
            int id = Integer.parseInt(getParam(ex, "suggestion"));
            List<Comment> list = commentService.getComments(id);
            JSONArray arr = new JSONArray();
            for (Comment c : list) {
                JSONObject o = new JSONObject();
                o.put("player", c.player.toString());
                o.put("content", c.content);
                o.put("timestamp", c.created.toString());
                arr.put(o);
            }
            writeJson(ex, new JSONObject().put("comments", arr));
        } else if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            JSONObject req = readJson(ex);
            String token = req.optString("token", null);
            UUID player = tokenService.checkToken(token);
            if (player == null) { ex.sendResponseHeaders(403, -1); return; }
            int id = req.optInt("suggestion", -1);
            String content = req.optString("content", "");
            if (id <= 0 || content.isEmpty()) { ex.sendResponseHeaders(400, -1); return; }
            commentService.addComment(id, player, content);
            writeJson(ex, new JSONObject().put("success", true));
        } else {
            ex.sendResponseHeaders(405, -1);
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

    private String getParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String p : q.split("&")) {
            String[] kv = p.split("=");
            if (kv.length == 2 && kv[0].equals(key)) return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }
}
