package com.dim.ws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;

/**
 * CDI bean used by server code to broadcast raw bytes/text to websocket clients (Vert.x server).
 */
@ApplicationScoped
public class RawDataBroadcaster {

    @Inject
    VertxWebSocketServer wsServer;

    public void broadcastBytes(byte[] data) {
        if (data == null) return;
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b));
        }
        sb.append(" | ");
        sb.append(new String(data, StandardCharsets.UTF_8));
        wsServer.broadcastText(sb.toString());
    }

    public void broadcastText(String text) {
        if (text == null) return;
        // If the payload is JSON and contains a room, route to room
        try {
            io.vertx.core.json.JsonObject json = new io.vertx.core.json.JsonObject(text);
            String room = json.getString("room", null);
            String payload = json.getString("payload", json.getString("message", text));
            if (room != null) {
                wsServer.broadcastToRoom(room, payload);
                return;
            }
        } catch (Exception ignored) {
            // not JSON, fallthrough
        }
        wsServer.broadcastText(text);
    }
}
