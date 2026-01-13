package com.dim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

@ApplicationScoped
public class RoomPersistenceService {

    @Inject
    MongoClient mongoClient;

    private MongoCollection<Document> getCollection() {
        MongoDatabase db = mongoClient.getDatabase("scigal");
        return db.getCollection("rooms");
    }

    public void createRoom(String roomId, String name, List<String> members) {
        Document doc = new Document()
            .append("roomId", roomId)
            .append("name", name)
            .append("members", members != null ? members : new ArrayList<>())
            .append("createdAt", Instant.now().toString());
        getCollection().insertOne(doc);
    }

    public void addMember(String roomId, String userId) {
        getCollection().updateOne(
            new Document("roomId", roomId),
            new Document("$addToSet", new Document("members", userId))
        );
    }

    public void removeMember(String roomId, String userId) {
        getCollection().updateOne(
            new Document("roomId", roomId),
            new Document("$pull", new Document("members", userId))
        );
    }

    public Document getRoom(String roomId) {
        return getCollection().find(new Document("roomId", roomId)).first();
    }

    public List<Document> getRoomsForUser(String userId) {
        List<Document> rooms = new ArrayList<>();
        getCollection().find(new Document("members", userId)).into(rooms);
        return rooms;
    }
}
