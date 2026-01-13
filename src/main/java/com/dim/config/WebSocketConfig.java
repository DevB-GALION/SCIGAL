package com.dim.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration pour le serveur WebSocket Socket.IO.
 * 
 * En Kubernetes, les ports sont automatiquement exposés via les variables:
 * - WEBSOCKET_SERVICE_PORT (si service nommé "websocket")
 * - Ou via WEBSOCKET_PORT défini dans deployment.yaml
 */
@ConfigMapping(prefix = "websocket")
public interface WebSocketConfig {

    /**
     * Adresse d'écoute du serveur WebSocket.
     * Par défaut: 0.0.0.0 (toutes les interfaces)
     */
    @WithDefault("0.0.0.0")
    String host();

    /**
     * Port d'écoute du serveur WebSocket.
     * Par défaut: 9092
     */
    @WithDefault("9092")
    int port();
    
    /**
     * Activer la détection automatique du port Kubernetes
     */
    @WithDefault("true")
    boolean autoDetectK8sPort();
}
