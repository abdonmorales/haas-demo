package edu.utexas.haas.web;

/** JSON request bodies. Detailed validation lives in the services so it's enforced for every caller. */
public final class Requests {

    private Requests() {
    }

    public record Login(String userId, String password) {
    }

    public record Register(String userId, String password, String confirmPassword) {
    }

    public record ChangePassword(String userId, String oldPassword, String newPassword, String confirmPassword) {
    }

    public record CreateProject(String projectId, String name, String description) {
    }

    public record Transfer(String projectId, Integer quantity) {
    }
}
