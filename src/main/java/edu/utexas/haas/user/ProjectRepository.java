package edu.utexas.haas.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ProjectRepository extends MongoRepository<Project, String> {

    Optional<Project> findByProjectKey(String projectKey);

    /** Matching a scalar against an array field finds documents whose array contains it. */
    @Query("{ 'memberIds': ?0 }")
    List<Project> findByMember(String userPk, Sort sort);
}
