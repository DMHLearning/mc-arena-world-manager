package dev.denismasterherobrine.arenaworldmanager.supervisor;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.denismasterherobrine.arenaworldmanager.ArenaWorldManager;
import dev.denismasterherobrine.arenaworldmanager.ArenaWorldManagerPlugin;
import dev.denismasterherobrine.arenaworldmanager.api.model.ArenaMapConfig;
import dev.denismasterherobrine.arenaworldmanager.runtime.RuntimeFlags;
import dev.denismasterherobrine.arenaworldmanager.config.MapConfigRegistry;
import dev.denismasterherobrine.arenaworldmanager.event.SupervisorActionAppliedEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * HTTP relay for ArenaWorldManager. Implements world-level supervisor actions:
 * RESTORE_ARENA, RESTART_ARENA, PAUSE_NEW_SESSIONS/RESUME_SESSIONS (preparation gate).
 */
public final class SupervisorRelayServer {

    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final ArenaWorldManager api;
    private final MapConfigRegistry registry;
    private final String bindHost;
    private final int port;
    private final String bearerToken;

    private HttpServer httpServer;

    public SupervisorRelayServer(JavaPlugin plugin, ArenaWorldManager api, MapConfigRegistry registry,
                                 String bindHost, int port, String bearerToken) {
        this.plugin = plugin;
        this.api = api;
        this.registry = registry;
        this.bindHost = bindHost;
        this.port = port;
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
    }

    public void start() throws IOException {
        InetSocketAddress addr = new InetSocketAddress(bindHost, port);
        httpServer = HttpServer.create(addr, 0);
        httpServer.createContext("/v1/execute", this::handleExecute);
        httpServer.setExecutor(r -> {
            try {
                r.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "world-manager relay task failed", e);
            }
        });
        httpServer.start();
        plugin.getLogger().info("ArenaWorldManager supervisor relay listening on http://"
                + bindHost + ":" + port + "/v1/execute");
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            httpServer = null;
        }
    }

    private void handleExecute(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }
        if (!authorize(ex)) {
            byte[] denied = "{\"success\":false,\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(401, denied.length);
            ex.getResponseBody().write(denied);
            ex.close();
            return;
        }

        JsonObject body;
        try (var reader = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            body = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            writeJson(ex, 400, false, "invalid JSON: " + e.getMessage(), null);
            return;
        }
        if (body == null) {
            writeJson(ex, 400, false, "empty body", null);
            return;
        }

        String commandId = jsonString(body, "command_id");
        String actionType = jsonString(body, "action_type");
        String target = jsonString(body, "target");
        Map<String, String> parameters = parseParameters(body);

        if (actionType.isEmpty()) {
            writeJson(ex, 400, false, "missing action_type", null);
            return;
        }

        plugin.getLogger().info(() -> "[WorldManagerRelay] recv command_id=" + commandId
                + " action=" + actionType + " target=" + target + " params=" + parameters);

        Result r = execute(actionType, target, parameters);

        if (r.implemented) {
            try {
                Bukkit.getGlobalRegionScheduler().execute(
                        ArenaWorldManagerPlugin.getPlugin(ArenaWorldManagerPlugin.class),
                        () -> Bukkit.getPluginManager().callEvent(new SupervisorActionAppliedEvent(
                                commandId, actionType, target, r.success, r.message, parameters)));
            } catch (Exception eventEx) {
                plugin.getLogger().log(Level.WARNING,
                        "[WorldManagerRelay] failed to dispatch SupervisorActionAppliedEvent", eventEx);
            }
        }

        int status = r.implemented ? 200 : 501;
        writeJson(ex, status, r.success, r.message, commandId);
    }

    private Result execute(String actionType, String arenaId, Map<String, String> parameters) {
        switch (actionType) {
            case "RESTORE_ARENA": {
                if (arenaId.isEmpty()) return Result.err("missing target (arena_id)");
                api.resetArena(arenaId).whenComplete((v, e) -> {
                    if (e != null) {
                        plugin.getLogger().warning("[WorldManagerRelay] RESTORE_ARENA failed arena_id="
                                + arenaId + ": " + e.getMessage());
                    } else {
                        plugin.getLogger().info("[WorldManagerRelay] RESTORE_ARENA completed arena_id=" + arenaId);
                    }
                });
                return Result.ok("RESTORE_ARENA: resetArena started (async) arena_id=" + arenaId);
            }
            case "RESTART_ARENA": {
                if (arenaId.isEmpty()) return Result.err("missing target (arena_id)");
                String mapId = parameters.getOrDefault("map_id", "");
                api.resetArena(arenaId).whenComplete((v, e) -> {
                    if (e != null) {
                        plugin.getLogger().warning("[WorldManagerRelay] RESTART_ARENA reset phase failed arena_id="
                                + arenaId + ": " + e.getMessage());
                        return;
                    }
                    Optional<ArenaMapConfig> cfg = mapId.isEmpty()
                            ? Optional.empty()
                            : registry.getConfig(mapId);
                    if (cfg.isPresent()) {
                        api.prepareArena(arenaId, cfg.get()).whenComplete((vv, ee) -> {
                            if (ee != null) {
                                plugin.getLogger().warning("[WorldManagerRelay] RESTART_ARENA prepare phase failed arena_id="
                                        + arenaId + ": " + ee.getMessage());
                            } else {
                                plugin.getLogger().info("[WorldManagerRelay] RESTART_ARENA completed arena_id=" + arenaId);
                            }
                        });
                    } else {
                        plugin.getLogger().info("[WorldManagerRelay] RESTART_ARENA: reset done, map_id unspecified; "
                                + "skipping prepare phase for arena_id=" + arenaId);
                    }
                });
                return Result.ok("RESTART_ARENA: reset+prepare chain scheduled arena_id=" + arenaId
                        + " map_id=" + (mapId.isEmpty() ? "<none>" : mapId));
            }
            case "PAUSE_NEW_SESSIONS":
                RuntimeFlags.prepareGateClosed = true;
                return Result.ok("PAUSE_NEW_SESSIONS: arena preparation gate closed");
            case "RESUME_SESSIONS":
                RuntimeFlags.prepareGateClosed = false;
                return Result.ok("RESUME_SESSIONS: arena preparation gate open");
            default:
                return Result.notImplemented("action not implemented on world-manager relay: " + actionType);
        }
    }

    private boolean authorize(HttpExchange ex) {
        if (bearerToken.isEmpty()) {
            plugin.getLogger().warning("ArenaWorldManager supervisor relay bearer-token is empty; refusing requests");
            return false;
        }
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null) return false;
        return ("Bearer " + bearerToken).equals(h.trim());
    }

    private static Map<String, String> parseParameters(JsonObject body) {
        Map<String, String> m = new HashMap<>();
        if (!body.has("parameters") || body.get("parameters").isJsonNull()) return m;
        JsonElement el = body.get("parameters");
        if (!el.isJsonObject()) return m;
        JsonObject p = el.getAsJsonObject();
        for (String key : p.keySet()) {
            JsonElement v = p.get(key);
            if (v != null && v.isJsonPrimitive()) {
                m.put(key, v.getAsString().trim());
            }
        }
        return m;
    }

    private static String jsonString(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return "";
        return o.get(key).getAsString().trim();
    }

    private static void writeJson(HttpExchange ex, int status, boolean success, String message, String commandId)
            throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("success", success);
        o.addProperty("message", message == null ? "" : message);
        if (commandId != null && !commandId.isEmpty()) {
            o.addProperty("command_id", commandId);
        }
        byte[] bytes = GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private record Result(boolean success, String message, boolean implemented) {
        static Result ok(String msg) { return new Result(true, msg, true); }
        static Result err(String msg) { return new Result(false, msg, true); }
        static Result notImplemented(String msg) { return new Result(false, msg, false); }
    }
}
