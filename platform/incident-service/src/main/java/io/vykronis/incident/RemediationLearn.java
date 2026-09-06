package io.vykronis.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The learn table (Phase 6 Unit 4): one immutable row per verified incident —
 * what was remediated, whether the service stayed healthy through the
 * verification window, and the exact window observed. Agents can read this to
 * learn which actions actually restore a service before recommending them.
 */
@Entity
@Table(name = "remediation_learn")
public class RemediationLearn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, unique = true, updatable = false)
    private String incidentId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private String serviceId;

    @Column(nullable = false, updatable = false)
    private String environment;

    @Column(nullable = false, updatable = false, length = 16)
    private String action;

    @Column(nullable = false, updatable = false, length = 16)
    private String result;

    @Column(name = "window_start", nullable = false, updatable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false, updatable = false)
    private Instant windowEnd;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    protected RemediationLearn() {
    }

    public RemediationLearn(String incidentId, String serviceId, String environment, String action,
                            String result, Instant windowStart, Instant windowEnd, Instant verifiedAt) {
        this.incidentId = incidentId;
        this.serviceId = serviceId;
        this.environment = environment;
        this.action = action;
        this.result = result;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.verifiedAt = verifiedAt;
    }

    public String incidentId() {
        return incidentId;
    }

    public String serviceId() {
        return serviceId;
    }

    public String environment() {
        return environment;
    }

    public String action() {
        return action;
    }

    public String result() {
        return result;
    }

    public Instant windowStart() {
        return windowStart;
    }

    public Instant windowEnd() {
        return windowEnd;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }
}