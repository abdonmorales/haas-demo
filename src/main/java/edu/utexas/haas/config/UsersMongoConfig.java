package edu.utexas.haas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.mongodb.client.MongoClient;

import edu.utexas.haas.user.Project;
import edu.utexas.haas.user.User;

/**
 * Database #1 — User Information: accounts and projects.
 * The factory is built inline, not as a bean: declaring a MongoDatabaseFactory bean would make
 * Spring Boot skip creating the shared MongoClient.
 * Every repository under {@code edu.utexas.haas.user} is bound to this database only.
 */
@Configuration
@EnableMongoRepositories(basePackages = "edu.utexas.haas.user", mongoTemplateRef = UsersMongoConfig.TEMPLATE)
public class UsersMongoConfig {

    public static final String TEMPLATE = "usersMongoTemplate";

    @Bean(TEMPLATE)
    @Primary // Boot's GridFsTemplate wants one default; our own code always injects by qualifier.
    public MongoTemplate usersMongoTemplate(MongoClient client, @Value("${haas.mongo.users-database}") String database,
                                            MappingMongoConverter converter) {
        MongoTemplate template = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, database), converter);
        // Indexes are the schema here: uniqueness is enforced by MongoDB, not by read-then-write checks.
        template.indexOps(User.class).ensureIndex(new Index("userIdLookup", Sort.Direction.ASC).unique());
        template.indexOps(Project.class).ensureIndex(new Index("projectKey", Sort.Direction.ASC).unique());
        template.indexOps(Project.class).ensureIndex(new Index("memberIds", Sort.Direction.ASC));
        return template;
    }
}
