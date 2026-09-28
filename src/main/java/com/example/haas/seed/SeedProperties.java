package com.example.haas.seed;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Simulated users, projects and hardware from {@code haas.seed} in application.yml. */
@ConfigurationProperties("haas.seed")
public record SeedProperties(
        String demoPassword,
        List<String> users,
        List<ProjectSeed> projects,
        List<HardwareSeed> hardware,
        List<CheckoutSeed> checkouts) {

    public SeedProperties {
        users = users == null ? List.of() : users;
        projects = projects == null ? List.of() : projects;
        hardware = hardware == null ? List.of() : hardware;
        checkouts = checkouts == null ? List.of() : checkouts;
    }

    public record ProjectSeed(String projectId, String name, String description, List<String> members) {
    }

    public record HardwareSeed(String name, String description, int capacity) {
    }

    public record CheckoutSeed(String projectId, String hwSet, int quantity) {
    }
}
