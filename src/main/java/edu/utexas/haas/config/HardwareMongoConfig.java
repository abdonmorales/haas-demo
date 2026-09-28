package edu.utexas.haas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.mongodb.client.MongoClient;

/**
 * Database #2 — Resource Information: hardware sets and per-project checkouts.
 * The factory is built inline, not as a bean: declaring a MongoDatabaseFactory bean would make
 * Spring Boot skip creating the shared MongoClient.
 * Every repository under {@code edu.utexas.haas.hardware} is bound to this database only.
 */
@Configuration
@EnableMongoRepositories(basePackages = "edu.utexas.haas.hardware", mongoTemplateRef = HardwareMongoConfig.TEMPLATE)
public class HardwareMongoConfig {

    public static final String TEMPLATE = "hardwareMongoTemplate";

    @Bean(TEMPLATE)
    public MongoTemplate hardwareMongoTemplate(MongoClient client, @Value("${haas.mongo.hardware-database}") String database,
                                               MappingMongoConverter converter) {
        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, database), converter);
    }
}
