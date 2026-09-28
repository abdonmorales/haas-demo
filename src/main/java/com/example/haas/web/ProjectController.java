package com.example.haas.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.haas.user.ProjectService;
import com.example.haas.user.ProjectView;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    public List<ProjectView> mine(HttpServletRequest request) {
        return projects.listFor(SessionUser.require(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectView create(@RequestBody Requests.CreateProject body, HttpServletRequest request) {
        return projects.create(SessionUser.require(request), body.projectId(), body.name(), body.description());
    }

    @PostMapping("/{projectId}/access")
    public ProjectView access(@PathVariable String projectId, HttpServletRequest request) {
        return projects.access(SessionUser.require(request), projectId);
    }
}
