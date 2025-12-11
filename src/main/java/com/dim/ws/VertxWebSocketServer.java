package com.dim.ws;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.dim.service.MessageService;
import com.dim.ws.PubSubService;

@ApplicationScoped
public class VertxWebSocketServer {

    @Inject
    Vertx vertx;

    @Inject
    MessageService messageService;

    @Inject
    PubSubService pubSubService;

    // sessions for clients not joined to a specific room
    private Set<ServerWebSocket> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // rooms -> set of sockets
    private Map<String, Set<ServerWebSocket>> rooms = new ConcurrentHashMap<>();

    // simple map to track last heartbeat timestamp per socket (presence)
    private Map<ServerWebSocket, Instant> lastHeartbeat = new ConcurrentHashMap<>();
    
    private io.vertx.core.http.HttpServer server;

    @PostConstruct
    void start() {
        // Start a separate Vert.x HTTP server for websockets on port 8888
        server = vertx.createHttpServer();
        server.webSocketHandler(ws -> {
            if ("/ws/raw".equals(ws.path())) {
                sessions.add(ws);
                lastHeartbeat.put(ws, Instant.now());
                ws.closeHandler(v -> {
                    sessions.remove(ws);
                    removeFromAllRooms(ws);
                    lastHeartbeat.remove(ws);
                });
                ws.textMessageHandler(msg -> handleText(ws, msg));
                // handle binary if needed
                ws.binaryMessageHandler(buffer -> handleBinary(ws, buffer));
            } else {
                ws.reject();
            }
        }).listen(8888, res -> {
            if (!res.succeeded()) {
                res.cause().printStackTrace();
            }
        });
    }

    @PreDestroy
    void stop() {
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {}
    }

    public void broadcastText(String text) {
        for (ServerWebSocket s : sessions) {
            if (!s.isClosed()) {
                s.writeTextMessage(text);
            }
        }
    }

    private void handleText(ServerWebSocket ws, String msg) {
        try {
            JsonObject json = new JsonObject(msg);
            String type = json.getString("type", "message");
                    switch (type) {
                case "join":
                    String room = json.getString("room");
                    joinRoom(room, ws);
                    break;
                case "leave":
                    room = json.getString("room");
                    leaveRoom(room, ws);
                    break;
                case "presence":
                    // heartbeat update
                    lastHeartbeat.put(ws, Instant.now());
                    break;
                case "message":
                default:
                    // route to room if present, else broadcast to all
                    String targetRoom = json.getString("room", null);
                    String payload = json.getString("payload", json.getString("message", ""));
                    String from = json.getString("from", null);
                    // persist message (best-effort)
                    if (targetRoom != null) {
                        messageService.saveMessage(targetRoom, from, payload);
                    } else {
                        messageService.saveMessage(null, from, payload);
                    }
                    // publish to redis so other instances can forward
                    try {
                        json.put("payload", payload);
                        pubSubService.publish(json);
                    } catch (Exception ignored) {}
                    // broadcast locally immediately
                    if (targetRoom != null) {
                        broadcastToRoom(targetRoom, payload);
                    } else {
                        broadcastText(payload);
                    }
                    break;
            }
        } catch (Exception e) {
            // not JSON — treat as plain text broadcast
            broadcastText(msg);
        }
    }

    private void handleBinary(ServerWebSocket ws, Buffer buffer) {
        // Placeholder for binary handling (e.g., raw bytes or WebRTC signalling blobs)
    }

    private void joinRoom(String room, ServerWebSocket ws) {
        rooms.computeIfAbsent(room, r -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(ws);
    }

    private void leaveRoom(String room, ServerWebSocket ws) {
        Set<ServerWebSocket> set = rooms.get(room);
        if (set != null) {
            set.remove(ws);
            if (set.isEmpty()) {
                rooms.remove(room);
            }
        }
    }

    private void removeFromAllRooms(ServerWebSocket ws) {
        for (Map.Entry<String, Set<ServerWebSocket>> e : rooms.entrySet()) {
            e.getValue().remove(ws);
        }
    }

    public void broadcastToRoom(String room, String text) {
        Set<ServerWebSocket> set = rooms.get(room);
        if (set == null) return;
        for (ServerWebSocket s : set) {
            if (!s.isClosed()) {
                s.writeTextMessage(text);
            }
        }
    }
}
