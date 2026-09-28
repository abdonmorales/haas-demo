package com.example.haas.user;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("projects")
public class Project {

    @Id
    private String id;

    /** The project code as the creator typed it (e.g. AMPLAB1); the hardware DB references this. */
    private String projectId;

    /** Lower-cased {@code projectId}; unique index, so "amplab1" and "AMPLAB1" can't both exist. */
    private String projectKey;

    private String name;

    private String description;

    private String ownerId;

    private Set<String> memberIds = new HashSet<>();

    private Instant createdAt = Instant.now();

    protected Project() {
    }

    public Project(String projectId, String name, String description, String ownerId) {
        this.projectId = projectId;
        this.projectKey = keyOf(projectId);
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.memberIds.add(ownerId);
    }

    static String keyOf(String projectId) {
        return projectId.trim().toLowerCase(Locale.ROOT);
    }

    public boolean hasMember(String userPk) {
        return memberIds.contains(userPk);
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Set<String> getMemberIds() {
        return memberIds;
    }
}
