package com.example.haas.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUserIdLookup(String userIdLookup);

    List<User> findByDemoTrueOrderByCreatedAtAsc();
}
