package com.dim.resource;

import jakarta.enterprise.context.ApplicationScoped; // Annotation CDI pour Quarkus
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/communication/{jwtToken}")
@ApplicationScoped // Utiliser @ApplicationScoped pour Quarkus
public class CommunicationSocket {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("jwtToken") String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            System.out.println("Validation du token échouée. Connexion refusée.");
            return;
        }

        String userId = jwtToken;
        session.getUserProperties().put("userId", userId);
        sessions.put(userId, session);
        System.out.println("Nouvelle connexion ! Utilisateur ID: " + userId + ", Session ID: " + session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        String userId = (String) session.getUserProperties().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            System.out.println("Session fermée pour l'utilisateur ID: " + userId);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String userId = (String) session.getUserProperties().get("userId");
        if (userId != null) {
            sessions.remove(userId);
            System.err.println("Erreur pour l'utilisateur ID: " + userId + ". Erreur: " + throwable.getMessage());
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String senderId = (String) session.getUserProperties().get("userId");
        System.out.println("Message reçu de " + senderId + ": " + message);

        if (message.contains("\"room_id\": \"TEST_ROOM\"")) {
            handleRoomMessage("TEST_ROOM", message, senderId);
        }
    }

    private void handleRoomMessage(String roomId, String message, String senderId) {
        broadcastToRoom(roomId, message, senderId);
    }

    private void broadcastToRoom(String roomId, String message, String senderId) {
        System.out.println("Diffusion ROOM [" + roomId + "] : " + message);
        sessions.forEach((id, session) -> {
            if (!id.equals(senderId)) {
                session.getAsyncRemote().sendText(message);
            }
        });
    }
}