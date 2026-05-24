package com.dotblog.auth.config;

import com.dotblog.auth.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Logs which Mongo database/collection auth-service uses and approximate document count
 * (helps catch missing {@code DATABASE_URI} / wrong working directory for {@code .env}).
 */
@Component
@Order(0)
public class MongoStartupDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoStartupDiagnostics.class);

    private final MongoTemplate mongoTemplate;

    public MongoStartupDiagnostics(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String db = mongoTemplate.getDb().getName();
        String coll = mongoTemplate.getCollectionName(User.class);
        long approx = mongoTemplate.getDb().getCollection(coll).estimatedDocumentCount();
        log.info("MongoDB: database='{}' collection='{}' estimatedDocuments≈{}", db, coll, approx);
    }
}
