package edu.utexas.haas.hardware;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface HardwareSetRepository extends MongoRepository<HardwareSet, String> {
}
