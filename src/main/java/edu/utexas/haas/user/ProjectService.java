package edu.utexas.haas.user;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import edu.utexas.haas.config.UsersMongoConfig;
import edu.utexas.haas.web.ApiException;

@Service
public class ProjectService {

    /**
     * Also keeps project IDs safe to use as MongoDB field names in the hardware DB
     * (no '.' or '$'), since checkouts are stored as {@code allocations.<projectId>}.
     */
    public static final Pattern PROJECT_ID = Pattern.compile("[A-Za-z0-9_-]{3,32}");

    private final ProjectRepository projects;
    private final MongoTemplate mongo;

    public ProjectService(ProjectRepository projects, @Qualifier(UsersMongoConfig.TEMPLATE) MongoTemplate mongo) {
        this.projects = projects;
        this.mongo = mongo;
    }

    public ProjectView create(String userPk, String projectId, String name, String description) {
        if (projectId == null || !PROJECT_ID.matcher(projectId.trim()).matches()) {
            throw ApiException.badRequest("Project ID must be 3-32 characters: letters, digits, '_' or '-'.");
        }
        if (name == null || name.isBlank() || name.length() > 80) {
            throw ApiException.badRequest("Project name is required (max 80 characters).");
        }
        if (description != null && description.length() > 500) {
            throw ApiException.badRequest("Description is limited to 500 characters.");
        }
        try {
            Project saved = projects.insert(new Project(projectId.trim(), name.trim(),
                    description == null ? "" : description.trim(), userPk));
            return ProjectView.of(saved, userPk);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Project ID " + projectId.trim() + " already exists — use 'Access project' to join it.");
        }
    }

    /**
     * "Use existing project": joins the caller to the project. {@code $addToSet} is a single atomic
     * update, so two people joining at once can't overwrite each other's membership.
     */
    public ProjectView access(String userPk, String projectId) {
        Project project = mongo.findAndModify(
                query(where("projectKey").is(key(projectId))),
                new Update().addToSet("memberIds", userPk),
                options().returnNew(true),
                Project.class);
        if (project == null) {
            throw ApiException.notFound("No project with ID " + projectId.trim() + ".");
        }
        return ProjectView.of(project, userPk);
    }

    public List<ProjectView> listFor(String userPk) {
        return projects.findByMember(userPk, Sort.by("projectKey")).stream()
                .map(p -> ProjectView.of(p, userPk))
                .toList();
    }

    /** Returns the canonical project ID if the user belongs to it; otherwise rejects the request. */
    public String requireMember(String userPk, String projectId) {
        Project project = projects.findByProjectKey(key(projectId))
                .orElseThrow(() -> ApiException.notFound("No project with ID " + projectId.trim() + "."));
        if (!project.hasMember(userPk)) {
            throw ApiException.forbidden("You are not a member of project " + project.getProjectId() + ".");
        }
        return project.getProjectId();
    }

    private static String key(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw ApiException.badRequest("Project ID is required.");
        }
        return Project.keyOf(projectId);
    }
}
