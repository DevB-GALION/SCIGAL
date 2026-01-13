package com.dim.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Détecte automatiquement les ports exposés dans un environnement Kubernetes.
 * 
 * Kubernetes injecte des variables d'environnement pour chaque service:
 * - {SERVICE_NAME}_SERVICE_HOST
 * - {SERVICE_NAME}_SERVICE_PORT
 */
@ApplicationScoped
public class KubernetesPortDetector {

    private static final Logger LOG = Logger.getLogger(KubernetesPortDetector.class);

    /**
     * Vérifie si on est dans un environnement Kubernetes
     */
    public boolean isRunningInKubernetes() {
        return System.getenv("KUBERNETES_SERVICE_HOST") != null;
    }

    /**
     * Détecte le port WebSocket depuis les variables Kubernetes
     */
    public Optional<Integer> detectWebSocketPort() {
        // Priorité 1: Variable explicite WEBSOCKET_PORT
        String wsPort = System.getenv("WEBSOCKET_PORT");
        if (wsPort != null) {
            try {
                int port = Integer.parseInt(wsPort);
                LOG.infof("WebSocket port from WEBSOCKET_PORT: %d", port);
                return Optional.of(port);
            } catch (NumberFormatException e) {
                LOG.warnf("Invalid WEBSOCKET_PORT value: %s", wsPort);
            }
        }

        // Priorité 2: Variable Kubernetes SCIGAL_COMM_SERVICE_PORT_WEBSOCKET
        String k8sWsPort = System.getenv("SCIGAL_COMM_SERVICE_PORT_WEBSOCKET");
        if (k8sWsPort != null) {
            try {
                int port = Integer.parseInt(k8sWsPort);
                LOG.infof("WebSocket port from K8s service: %d", port);
                return Optional.of(port);
            } catch (NumberFormatException e) {
                LOG.warnf("Invalid K8s service port: %s", k8sWsPort);
            }
        }

        // Priorité 3: Port conteneur
        String containerPort = System.getenv("WEBSOCKET_CONTAINER_PORT");
        if (containerPort != null) {
            try {
                int port = Integer.parseInt(containerPort);
                LOG.infof("WebSocket port from container: %d", port);
                return Optional.of(port);
            } catch (NumberFormatException e) {
                LOG.warnf("Invalid container port: %s", containerPort);
            }
        }

        return Optional.empty();
    }

    /**
     * Détecte l'hôte depuis les variables Kubernetes
     */
    public Optional<String> detectHost() {
        if (isRunningInKubernetes()) {
            return Optional.of("0.0.0.0");
        }

        String wsHost = System.getenv("WEBSOCKET_HOST");
        if (wsHost != null) {
            return Optional.of(wsHost);
        }

        return Optional.empty();
    }

    /**
     * Affiche les infos de debug Kubernetes
     */
    public void logKubernetesInfo() {
        if (isRunningInKubernetes()) {
            LOG.info("=== Running in Kubernetes ===");
            LOG.infof("Pod Name: %s", System.getenv("HOSTNAME"));
            LOG.infof("Pod IP: %s", System.getenv("POD_IP"));
            LOG.infof("Node Name: %s", System.getenv("NODE_NAME"));
            LOG.infof("Namespace: %s", System.getenv("POD_NAMESPACE"));
        } else {
            LOG.info("Running outside Kubernetes (local/dev mode)");
        }
    }
}
