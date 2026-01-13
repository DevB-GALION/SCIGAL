package com.dim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;

@ApplicationScoped
public class UserProfileService {

    @Inject
    MongoClient mongoClient;

    private MongoCollection<Document> getCollection() {
        MongoDatabase db = mongoClient.getDatabase("scigal");
        return db.getCollection("profiles");
    }

    public void saveProfile(String odutilisateur, String displayName, String avatarUrl) {
        Document doc = new Document()
            .append("userId", odutilisateur)
            .append("displayName", displayName)
            .append("avatarUrl", avatarUrl)
            .append("status", "online")
            .append("lastSeen", Instant.now().toString());
        getCollection().insertOne(doc);
    }

    public Document getProfile(String odutilisateur) {
        return getCollection().find(new Document("userId", odutilisateur)).first();
    }

    public void updateStatus(String odutilisateur, String status) {
        getCollection().updateOne(
            new Document("userId", odutilisateur),
            new Document("$set", new Document("status", status)
                .append("lastSeen", Instant.now().toString()))
        );
    }
}
