package edu.utexas.haas.hardware;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import edu.utexas.haas.config.HardwareMongoConfig;
import edu.utexas.haas.user.ProjectService;
import edu.utexas.haas.web.ApiException;

/**
 * Checkout / check-in rules. Callers must already have verified project membership
 * (that lives in the users database); this service trusts the project ID it is given.
 */
@Service
public class HardwareService {

    /** Upper bound on a single request, so absurd inputs are rejected before touching the DB. */
    static final int MAX_REQUEST = 10_000;

    private final HardwareSetRepository sets;
    private final MongoTemplate mongo;

    public HardwareService(HardwareSetRepository sets, @Qualifier(HardwareMongoConfig.TEMPLATE) MongoTemplate mongo) {
        this.sets = sets;
        this.mongo = mongo;
    }

    public List<HardwareView> list(String projectId) {
        return sets.findAll(Sort.by("_id")).stream()
                .map(s -> toView(s, projectId))
                .toList();
    }

    /**
     * The filter {@code available >= qty} and the two {@code $inc}s run as one atomic operation,
     * so two simultaneous checkouts can never push availability below zero.
     */
    public HardwareView checkOut(String projectId, String setName, int qty) {
        validate(projectId, qty);
        HardwareSet updated = mongo.findAndModify(
                query(where("_id").is(setName).and("available").gte(qty)),
                new Update().inc("available", -qty).inc(allocationField(projectId), qty),
                options().returnNew(true),
                HardwareSet.class);
        if (updated == null) {
            HardwareSet set = requireSet(setName);
            throw ApiException.conflict("Only " + set.getAvailable() + " unit(s) of " + set.getName()
                    + " are available; you requested " + qty + ".");
        }
        return toView(updated, projectId);
    }

    /** Mirror of {@link #checkOut}: only succeeds if the project really holds at least {@code qty}. */
    public HardwareView checkIn(String projectId, String setName, int qty) {
        validate(projectId, qty);
        String field = allocationField(projectId);
        HardwareSet updated = mongo.findAndModify(
                query(where("_id").is(setName).and(field).gte(qty)),
                new Update().inc("available", qty).inc(field, -qty),
                options().returnNew(true),
                HardwareSet.class);
        if (updated == null) {
            HardwareSet set = requireSet(setName);
            throw ApiException.conflict("Project " + projectId + " only holds " + set.heldBy(projectId)
                    + " unit(s) of " + set.getName() + "; cannot return " + qty + ".");
        }
        if (updated.heldBy(projectId) == 0) {
            // Tidy up zero entries — conditional, so a checkout that raced in between is left alone.
            mongo.updateFirst(query(where("_id").is(setName).and(field).is(0)), new Update().unset(field), HardwareSet.class);
        }
        return toView(updated, projectId);
    }

    /** Used by the seeder only: create a set if missing. */
    public void ensureSet(String name, String description, int capacity) {
        if (!sets.existsById(name)) {
            sets.insert(new HardwareSet(name, description, capacity));
        }
    }

    public boolean isEmpty() {
        return sets.count() == 0;
    }

    private HardwareSet requireSet(String name) {
        return sets.findById(name).orElseThrow(() -> ApiException.notFound("No hardware set named " + name + "."));
    }

    private static String allocationField(String projectId) {
        return "allocations." + projectId;
    }

    private static HardwareView toView(HardwareSet set, String projectId) {
        return new HardwareView(set.getName(), set.getDescription(), set.getCapacity(), set.getAvailable(),
                set.heldBy(projectId));
    }

    private static void validate(String projectId, int qty) {
        // projectId becomes part of a field path; never let '.' or '$' through, whoever the caller is.
        if (projectId == null || !ProjectService.PROJECT_ID.matcher(projectId).matches()) {
            throw new IllegalArgumentException("Invalid project ID: " + projectId);
        }
        if (qty <= 0) {
            throw ApiException.badRequest("Quantity must be a positive whole number.");
        }
        if (qty > MAX_REQUEST) {
            throw ApiException.badRequest("Quantity cannot exceed " + MAX_REQUEST + " per request.");
        }
    }
}
