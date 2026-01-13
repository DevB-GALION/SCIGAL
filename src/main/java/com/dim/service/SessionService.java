package com.dim.service;

import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.function.Consumer;

@ApplicationScoped
public class SessionService {

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

    public void saveSession(String sessionId, String userId, int ttlSeconds) {
        if (redis != null) {
            redis.setex("session:" + sessionId, String.valueOf(ttlSeconds), userId);
        }
    }

    public void getSession(String sessionId, Consumer<String> callback) {
        if (redis != null) {
            redis.get("session:" + sessionId).onSuccess(response -> {
                callback.accept(response != null ? response.toString() : null);
            });
        }
    }

    public void deleteSession(String sessionId) {
        if (redis != null) {
            redis.del(List.of("session:" + sessionId));
        }
    }

    public void setUserOnline(String userId) {
        if (redis != null) {
            redis.set(List.of("presence:" + userId, "online"));
            redis.expire(List.of("presence:" + userId, "300"));
        }
    }

    public void setUserOffline(String userId) {
        if (redis != null) {
            redis.del(List.of("presence:" + userId));
        }
    }
}
