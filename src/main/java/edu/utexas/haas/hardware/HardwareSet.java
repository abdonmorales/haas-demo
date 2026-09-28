package edu.utexas.haas.hardware;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One hardware set, including how many units each project holds. Keeping the per-project counts
 * inside the same document means a checkout (take from {@code available}, add to the project's
 * count) is a single-document update, which MongoDB applies atomically.
 * <p>Invariant: {@code available + sum(allocations) == capacity}.
 */
@Document("hardware_sets")
public class HardwareSet {

    @Id
    private String name;

    private String description;

    private int capacity;

    private int available;

    /** projectId -> units checked out. Projects live in the other database, so this is by code, not reference. */
    private Map<String, Integer> allocations = new HashMap<>();

    protected HardwareSet() {
    }

    public HardwareSet(String name, String description, int capacity) {
        this.name = name;
        this.description = description;
        this.capacity = capacity;
        this.available = capacity;
    }

    public int heldBy(String projectId) {
        return projectId == null ? 0 : allocations.getOrDefault(projectId, 0);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailable() {
        return available;
    }

    public Map<String, Integer> getAllocations() {
        return allocations;
    }
}
