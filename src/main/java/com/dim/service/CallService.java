package com.dim.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

@ApplicationScoped
public class CallService {

    @Inject
    MongoClient mongoClient;

    /**
     * Save call metadata (best-effort). The metadata JSON node is stored as a document.
     */
    public void saveCallMetadata(String callId, String from, String to, JsonNode metadata) {
        try {
            MongoDatabase db = mongoClient.getDatabase("scigal");
            MongoCollection<Document> coll = db.getCollection("calls");
            Document doc = new Document();
            doc.append("callId", callId);
            doc.append("from", from);
            doc.append("to", to);
            doc.append("metadata", metadata == null ? null : Document.parse(metadata.toString()));
            doc.append("timestamp", Instant.now().toString());
            coll.insertOne(doc);
        } catch (Exception e) {
            // best-effort: do not fail
            e.printStackTrace();
        }
    }
}
