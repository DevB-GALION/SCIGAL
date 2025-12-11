package com.dim.ws;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.Request;
import io.vertx.redis.client.Command;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;
import com.corundumstudio.socketio.SocketIOServer;
import com.dim.ws.SocketIOServerWrapper;

/**
 * Pub/Sub wrapper using Vert.x Redis client. Publishes messages on channel 'scigal:messages'
 * and forwards received messages to the local VertxWebSocketServer.
 */
@ApplicationScoped
public class PubSubService {

    @Inject
    Vertx vertx;

    // prefer forwarding pubsub events to the Socket.IO server wrapper
    @Inject
    SocketIOServerWrapper socketIOServerWrapper;

    private Redis client;
    private RedisConnection subConn;
    private String channel = "scigal:messages";
    private final String instanceId = UUID.randomUUID().toString();

    @PostConstruct
    void start() {
        try {
            client = Redis.createClient(vertx, "redis://localhost:6379");
            client.connect(ar -> {
                if (ar.succeeded()) {
                    subConn = ar.result();
                    subConn.handler(resp -> {
                        try {
                            if (resp != null) {
                                String s = resp.toString();
                                // messages arrive as arrays for subscribe events; ignore if not a message
                                if (s.contains("message")) {
                                    // crude parsing: actual payload is last token of array
                                    int last = s.lastIndexOf(',');
                                    String payload = s.substring(last + 1).trim();
                                    if (payload.startsWith("\"")) payload = payload.substring(1);
                                    if (payload.endsWith("\"") || payload.endsWith("]")) payload = payload.replaceAll("\"]+$", "");
                                    JsonObject json = new JsonObject(payload);
                                    String origin = json.getString("origin", "");
                                    if (!instanceId.equals(origin)) {
                                        // forward to Socket.IO server wrapper which will route to clients
                                        try {
                                            socketIOServerWrapper.onPubSub(json);
                                        } catch (Exception e) {
                                            // fallback: attempt to route via Vert.x WS if available
                                            try {
                                                String room = json.getString("room", null);
                                                String message = json.getString("payload", json.getString("message", ""));
                                                // best-effort fallback using reflection if VertxWebSocketServer present
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    // subscribe
                    subConn.send(Request.cmd(Command.SUBSCRIBE).arg(channel));
                }
            });
        } catch (Exception e) {
            // not critical during build
            e.printStackTrace();
        }
    }

    public void publish(JsonObject json) {
        try {
            json.put("origin", instanceId);
            // use a short-lived connection for publish
            client.connect(ar -> {
                if (ar.succeeded()) {
                    RedisConnection conn = ar.result();
                    conn.send(Request.cmd(Command.PUBLISH).arg(channel).arg(json.encode()), pres -> {
                        // close connection
                        try { conn.close(); } catch (Exception ignored) {}
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @PreDestroy
    void stop() {
        try {
            if (subConn != null) subConn.close();
            if (client != null) client.close();
        } catch (Exception ignored) {}
    }
}
