package com.dim.service;

import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@ApplicationScoped
public class CacheService {

    @Inject
    Vertx vertx;

    private RedisAPI redis;

    @PostConstruct
    void init() {
        Redis.createClient(vertx, "redis://localhost:6379").connect(ar -> {
            if (ar.succeeded()) {
                redis = RedisAPI.api(ar.result());
            }
        });
    }

    /**
     * Cache les N derniers messages d'une room (liste Redis)
     */
    public void cacheMessage(String roomId, String message, int maxMessages) {
        if (redis != null) {
            String key = "room:messages:" + roomId;
            redis.lpush(List.of(key, message));
            redis.ltrim(key, "0", String.valueOf(maxMessages - 1));
        }
    }

    public void getRecentMessages(String roomId, int count, Consumer<List<String>> callback) {
        if (redis != null) {
            redis.lrange("room:messages:" + roomId, "0", String.valueOf(count - 1)).onSuccess(response -> {
                List<String> messages = new ArrayList<>();
                if (response != null) {
                    response.forEach(r -> messages.add(r.toString()));
                }
                callback.accept(messages);
            });
        }
    }

    /**
     * Stocke l'état d'une room (nombre de membres connectés, etc.)
     */
    public void setRoomState(String roomId, String state) {
        if (redis != null) {
            redis.set(List.of("room:state:" + roomId, state));
        }
    }

    public void getRoomState(String roomId, Consumer<String> callback) {
        if (redis != null) {
            redis.get("room:state:" + roomId).onSuccess(response -> {
                callback.accept(response != null ? response.toString() : null);
            });
        }
    }
}
