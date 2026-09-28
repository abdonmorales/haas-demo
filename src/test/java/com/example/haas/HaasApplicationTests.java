package com.example.haas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.haas.config.HardwareMongoConfig;
import com.example.haas.config.UsersMongoConfig;
import com.example.haas.hardware.HardwareService;
import com.example.haas.hardware.HardwareView;
import com.example.haas.user.ProjectService;
import com.example.haas.user.UserService;
import com.example.haas.web.ApiException;

@SpringBootTest
@ActiveProfiles("test")
class HaasApplicationTests {

    @Autowired UserService users;
    @Autowired ProjectService projects;
    @Autowired HardwareService hardware;
    @Autowired @Qualifier(UsersMongoConfig.TEMPLATE) MongoTemplate usersDb;
    @Autowired @Qualifier(HardwareMongoConfig.TEMPLATE) MongoTemplate hardwareDb;

    @Test
    void userIdAndPasswordAreNeverStoredInPlaintext() {
        users.register("plainCheck", "Secret#123", "Secret#123", false);

        var raw = usersDb.getCollection("users").find().into(new java.util.ArrayList<Document>());
        assertThat(raw.toString()).doesNotContain("plainCheck").doesNotContain("Secret#123");
        assertThat(users.authenticate("PLAINCHECK", "Secret#123")).isNotNull(); // IDs are case-insensitive
        assertThatThrownBy(() -> users.authenticate("plainCheck", "wrong-pass")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> users.register("PlainCheck", "Secret#123", "Secret#123", false))
                .isInstanceOf(ApiException.class).hasMessageContaining("taken");
    }

    @Test
    void theTwoDatabasesHoldSeparateCollections() {
        assertThat(usersDb.getDb().getName()).isNotEqualTo(hardwareDb.getDb().getName());
        assertThat(usersDb.getCollectionNames()).contains("users", "projects").doesNotContain("hardware_sets");
        assertThat(hardwareDb.getCollectionNames()).contains("hardware_sets").doesNotContain("users");
    }

    @Test
    void checkoutAndCheckinKeepCountsConsistent() {
        HardwareView before = find("Oscilloscopes", "TEST1");

        HardwareView out = hardware.checkOut("TEST1", "Oscilloscopes", 5);
        assertThat(out.available()).isEqualTo(before.available() - 5);
        assertThat(out.checkedOut()).isEqualTo(5);

        HardwareView back = hardware.checkIn("TEST1", "Oscilloscopes", 5);
        assertThat(back.available()).isEqualTo(before.available());
        assertThat(back.checkedOut()).isZero();
    }

    @Test
    void cannotOverdrawOrOverReturn() {
        HardwareView set = find("Function-Generators", "TEST2");
        assertThatThrownBy(() -> hardware.checkOut("TEST2", "Function-Generators", set.available() + 1))
                .isInstanceOf(ApiException.class).hasMessageContaining("available");
        assertThatThrownBy(() -> hardware.checkIn("TEST2", "Function-Generators", 1))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> hardware.checkOut("TEST2", "Function-Generators", 0))
                .isInstanceOf(ApiException.class);
        assertThat(find("Function-Generators", "TEST2").available()).isEqualTo(set.available());
    }

    @Test
    void concurrentCheckoutsNeverOversell() throws InterruptedException {
        hardware.ensureSet("RaceSet", "contention test", 50);
        AtomicInteger succeeded = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 40; i++) {
            String project = "RACE" + (i % 4);
            pool.submit(() -> {
                try {
                    hardware.checkOut(project, "RaceSet", 5);
                    succeeded.incrementAndGet();
                } catch (ApiException expectedWhenSoldOut) {
                    // fine
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).isEqualTo(10); // 50 units / 5 per request
        assertThat(find("RaceSet", "RACE0").available()).isZero();
    }

    @Test
    void projectIdsCannotInjectMongoFieldPaths() {
        assertThatThrownBy(() -> hardware.checkOut("x.$y", "Oscilloscopes", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonMembersCannotUseAProject() {
        String outsider = users.register("outsider", "Outsider#1", "Outsider#1", false).getId();
        assertThatThrownBy(() -> projects.requireMember(outsider, "AMPLAB1"))
                .isInstanceOf(ApiException.class).hasMessageContaining("not a member");
        projects.access(outsider, "amplab1");
        assertThat(projects.requireMember(outsider, "AMPLAB1")).isEqualTo("AMPLAB1");
    }

    private HardwareView find(String name, String projectId) {
        return hardware.list(projectId).stream().filter(h -> h.name().equals(name)).findFirst().orElseThrow();
    }
}
