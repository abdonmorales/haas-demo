package edu.utexas.haas.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.utexas.haas.hardware.HardwareService;
import edu.utexas.haas.hardware.HardwareView;
import edu.utexas.haas.user.ProjectService;

/**
 * The only place the two databases meet: membership is checked against the users DB,
 * then the hardware DB is updated. Neither service knows about the other.
 */
@RestController
@RequestMapping("/api/hardware")
public class HardwareController {

    private final HardwareService hardware;
    private final ProjectService projects;

    public HardwareController(HardwareService hardware, ProjectService projects) {
        this.hardware = hardware;
        this.projects = projects;
    }

    @GetMapping
    public List<HardwareView> list(@RequestParam(required = false) String projectId, HttpServletRequest request) {
        String userPk = SessionUser.require(request);
        return hardware.list(projectId == null ? null : projects.requireMember(userPk, projectId));
    }

    @PostMapping("/{setName}/checkout")
    public HardwareView checkOut(@PathVariable String setName, @RequestBody Requests.Transfer body,
                                 HttpServletRequest request) {
        String projectId = projects.requireMember(SessionUser.require(request), body.projectId());
        return hardware.checkOut(projectId, setName, quantity(body));
    }

    @PostMapping("/{setName}/checkin")
    public HardwareView checkIn(@PathVariable String setName, @RequestBody Requests.Transfer body,
                                HttpServletRequest request) {
        String projectId = projects.requireMember(SessionUser.require(request), body.projectId());
        return hardware.checkIn(projectId, setName, quantity(body));
    }

    private static int quantity(Requests.Transfer body) {
        if (body.quantity() == null) {
            throw ApiException.badRequest("Quantity is required.");
        }
        return body.quantity();
    }
}
