package edu.utexas.haas.user;

/** What the API exposes about a project — internal PKs and member IDs stay server-side. */
public record ProjectView(String projectId, String name, String description, int memberCount, boolean owner) {

    static ProjectView of(Project project, String viewerPk) {
        return new ProjectView(project.getProjectId(), project.getName(), project.getDescription(),
                project.getMemberIds().size(), project.getOwnerId().equals(viewerPk));
    }
}
