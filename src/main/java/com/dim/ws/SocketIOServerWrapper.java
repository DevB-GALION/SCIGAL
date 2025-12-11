package com.dim.ws;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.SocketIOClient;

import com.dim.service.MessageService;
import com.dim.service.CallService;
import com.dim.ws.PubSubService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
public class SocketIOServerWrapper {

    private static final Logger LOG = Logger.getLogger(SocketIOServerWrapper.class);

    @Inject
    PubSubService pubSubService;

    @Inject
    MessageService messageService;

    @Inject
    CallService callService;

    private SocketIOServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    void onStart(@Observes StartupEvent ev) {
        start();
    }

    @PostConstruct
    void start() {
        LOG.info("Starting Socket.IO server on port 9092...");
        try {
            Configuration config = new Configuration();
            config.setHostname("0.0.0.0");
            config.setPort(9092);
            server = new SocketIOServer(config);

            // connection events
            server.addConnectListener(client -> {
                LOG.infof("Socket.IO client connected: %s", client.getSessionId());
                try {
                    // inform client of its session id so clients may target each other
                    client.sendEvent("connected", client.getSessionId().toString());
                } catch (Exception ignored) {}
            });
            server.addDisconnectListener(client -> {
                LOG.infof("Socket.IO client disconnected: %s", client.getSessionId());
            });

            // join room
            server.addEventListener("join", String.class, (client, data, ackSender) -> {
                try {
                    JsonNode node = mapper.readTree(data);
                    String room = node.has("room") ? node.get("room").asText() : null;
                    if (room != null) {
                        client.joinRoom(room);
                        LOG.infof("client %s joined room %s", client.getSessionId(), room);
                    }
                } catch (Exception e) {
                    LOG.warn("invalid join payload", e);
                }
            });

            // leave room
            server.addEventListener("leave", String.class, (client, data, ackSender) -> {
                try {
                    JsonNode node = mapper.readTree(data);
                    String room = node.has("room") ? node.get("room").asText() : null;
                    if (room != null) {
                        client.leaveRoom(room);
                        LOG.infof("client %s left room %s", client.getSessionId(), room);
                    }
                } catch (Exception e) {
                    LOG.warn("invalid leave payload", e);
                }
            });

            // chat/message
            server.addEventListener("message", String.class, (client, data, ackSender) -> {
                try {
                    JsonNode node = mapper.readTree(data);
                    String room = node.has("room") ? node.get("room").asText() : null;
                    String from = node.has("from") ? node.get("from").asText() : null;
                    String payload = node.has("payload") ? node.get("payload").asText() : node.toString();
                    // persist message
                    try {
                        messageService.saveMessage(room, from, payload);
                    } catch (Exception ex) {
                        LOG.debug("message persistence failed (best-effort)", ex);
                    }
                    // publish to other instances
                    try {
                        pubSubService.publish(new JsonObject(node.toString()));
                    } catch (Exception ex) {
                        LOG.debug("redis publish failed (best-effort)", ex);
                    }
                    // broadcast locally
                    if (room != null) {
                        server.getRoomOperations(room).sendEvent("message", payload);
                    } else {
                        server.getBroadcastOperations().sendEvent("message", payload);
                    }
                } catch (Exception e) {
                    LOG.warn("invalid message payload", e);
                }
            });

            // WebRTC signalling (offer/answer/ice). Payload must include target session id or room
            server.addEventListener("signal", String.class, (client, data, ackSender) -> {
                try {
                    JsonNode node = mapper.readTree(data);
                    String target = node.has("target") ? node.get("target").asText() : null;
                    String room = node.has("room") ? node.get("room").asText() : null;
                    // forward to target if provided
                    if (target != null) {
                        try {
                            java.util.UUID targetId = java.util.UUID.fromString(target);
                            SocketIOClient targetClient = server.getClient(targetId);
                            if (targetClient != null) {
                                targetClient.sendEvent("signal", node.toString());
                            }
                        } catch (IllegalArgumentException iae) {
                            LOG.debug("invalid target id", iae);
                        }
                    } else if (room != null) {
                        server.getRoomOperations(room).sendEvent("signal", node.toString());
                    } else {
                        // broadcast as fallback
                        server.getBroadcastOperations().sendEvent("signal", node.toString());
                    }
                    // also publish to Redis so other instances can relay
                    try {
                        pubSubService.publish(new JsonObject(node.toString()));
                    } catch (Exception ex) {
                        LOG.debug("redis publish for signal failed", ex);
                    }
                } catch (Exception e) {
                    LOG.warn("invalid signal payload", e);
                }
            });

            // call metadata event (store metadata about calls)
            server.addEventListener("call_metadata", String.class, (client, data, ackSender) -> {
                try {
                    JsonNode node = mapper.readTree(data);
                    String callId = node.has("callId") ? node.get("callId").asText() : null;
                    String from = node.has("from") ? node.get("from").asText() : null;
                    String to = node.has("to") ? node.get("to").asText() : null;
                    // store metadata
                    callService.saveCallMetadata(callId, from, to, node);
                    // publish to redis for cross-instance visibility
                    try {
                        pubSubService.publish(new JsonObject(node.toString()));
                    } catch (Exception ex) {
                        LOG.debug("redis publish for call_metadata failed", ex);
                    }
                } catch (Exception e) {
                    LOG.warn("invalid call_metadata payload", e);
                }
            });

            server.start();
            LOG.info("Socket.IO server started on port 9092");
        } catch (Exception e) {
            LOG.error("Failed to start Socket.IO server", e);
        }
    }

    @PreDestroy
    void stop() {
        try {
            if (server != null) server.stop();
        } catch (Exception ignored) {}
    }

    /**
     * Called by the PubSubService when a message arrives from Redis from another instance.
     * Routes the JSON to the appropriate Socket.IO event (message, signal, call_metadata).
     */
    public void onPubSub(JsonObject json) {
        try {
            String type = json.getString("type", "message");
            switch (type) {
                case "signal":
                    String target = json.getString("target", null);
                    String room = json.getString("room", null);
                    if (target != null) {
                        try {
                            java.util.UUID targetId = java.util.UUID.fromString(target);
                            SocketIOClient targetClient = server.getClient(targetId);
                            if (targetClient != null) targetClient.sendEvent("signal", json.encode());
                        } catch (IllegalArgumentException ignored) {}
                    } else if (room != null) {
                        server.getRoomOperations(room).sendEvent("signal", json.encode());
                    } else {
                        server.getBroadcastOperations().sendEvent("signal", json.encode());
                    }
                    break;
                case "call_metadata":
                    // forward call metadata events to clients
                    String callRoom = json.getString("room", null);
                    if (callRoom != null) {
                        server.getRoomOperations(callRoom).sendEvent("call_metadata", json.encode());
                    } else {
                        server.getBroadcastOperations().sendEvent("call_metadata", json.encode());
                    }
                    break;
                case "message":
                default:
                    String payload = json.getString("payload", json.getString("message", ""));
                    String msgRoom = json.getString("room", null);
                    if (msgRoom != null) {
                        server.getRoomOperations(msgRoom).sendEvent("message", payload);
                    } else {
                        server.getBroadcastOperations().sendEvent("message", payload);
                    }
                    break;
            }
        } catch (Exception e) {
            LOG.debug("failed to forward pubsub message to socket.io clients", e);
        }
    }

    /**
     * Broadcast plain text to all connected Socket.IO clients.
     */
    public void broadcastText(String text) {
        try {
            server.getBroadcastOperations().sendEvent("message", text);
        } catch (Exception e) {
            LOG.debug("failed to broadcast text to socket.io clients", e);
        }
    }

    /**
     * Broadcast text to a specific room.
     */
    public void broadcastToRoom(String room, String text) {
        try {
            server.getRoomOperations(room).sendEvent("message", text);
        } catch (Exception e) {
            LOG.debug("failed to broadcast to room to socket.io clients", e);
        }
    }

}
