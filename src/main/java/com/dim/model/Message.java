package com.dim.model;

import java.time.Instant;

public class Message {
    private String id;
    private String room;
    private String from;
    private String payload;
    private Instant timestamp;

    public Message() {}

    public Message(String room, String from, String payload, Instant timestamp) {
        this.room = room;
        this.from = from;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
