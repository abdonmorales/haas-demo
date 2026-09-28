package edu.utexas.haas.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import edu.utexas.haas.hardware.HardwareService;
import edu.utexas.haas.user.ProjectService;
import edu.utexas.haas.user.UserRepository;
import edu.utexas.haas.user.UserService;

/**
 * Loads simulated data on first start. Each database is seeded independently and only when empty,
 * so restarting never duplicates rows and never overwrites what users have done in the demo.
 * Runs after all beans exist but before the web server starts, so no request ever sees a half-seeded app.
 */
@Component
public class DemoDataSeeder implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final SeedProperties seed;
    private final UserRepository userRepository;
    private final UserService users;
    private final ProjectService projects;
    private final HardwareService hardware;

    public DemoDataSeeder(SeedProperties seed, UserRepository userRepository, UserService users,
                          ProjectService projects, HardwareService hardware) {
        this.seed = seed;
        this.userRepository = userRepository;
        this.users = users;
        this.projects = projects;
        this.hardware = hardware;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean seedHardware = hardware.isEmpty();
        if (userRepository.count() == 0) {
            seedUsersAndProjects();
        }
        if (seedHardware) {
            seedHardware();
        }
    }

    private void seedUsersAndProjects() {
        for (String userId : seed.users()) {
            users.register(userId, seed.demoPassword(), seed.demoPassword(), true);
        }
        for (var p : seed.projects()) {
            var members = p.members();
            String owner = users.requireUserPk(members.getFirst());
            projects.create(owner, p.projectId(), p.name(), p.description());
            for (String member : members.subList(1, members.size())) {
                projects.access(users.requireUserPk(member), p.projectId());
            }
        }
        log.info("Seeded users DB with {} demo users and {} projects", seed.users().size(), seed.projects().size());
    }

    private void seedHardware() {
        for (var h : seed.hardware()) {
            hardware.ensureSet(h.name(), h.description(), h.capacity());
        }
        for (var c : seed.checkouts()) {
            hardware.checkOut(c.projectId(), c.hwSet(), c.quantity());
        }
        log.info("Seeded hardware DB with {} hardware sets", seed.hardware().size());
    }
}
