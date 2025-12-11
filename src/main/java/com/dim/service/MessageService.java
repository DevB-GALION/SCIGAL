package com.dim.service;

import com.dim.model.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.time.Instant;

@ApplicationScoped
public class MessageService {

    @Inject
    MongoClient mongoClient;

    public void saveMessage(String room, String from, String payload) {
        try {
            MongoDatabase db = mongoClient.getDatabase("scigal");
            MongoCollection<Document> coll = db.getCollection("messages");
            Document doc = new Document();
            doc.append("room", room);
            doc.append("from", from);
            doc.append("payload", payload);
            doc.append("timestamp", Instant.now().toString());
            coll.insertOne(doc);
        } catch (Exception e) {
            // swallow - persistence failure should not break routing; log in real app
            e.printStackTrace();
        }
    }
}
