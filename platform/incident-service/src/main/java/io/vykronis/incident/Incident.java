package io.vykronis.incident;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA aggregate for an incident. Backed by the {@code incidents} table created
 * in {@code V1__create_incidents_table.sql}. Fields line up with the columns
 * so no further migration is needed for the Phase 2 path.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false, unique = true, updatable = false)
    private String incidentId;

    @Column(nullable = false)
    private String source;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(nullable = false)
    private String env;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "error_rate")
    private Double errorRate;

    @Column(name = "error_count")
    private Integer errorCount;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "trace_id")
    private String traceId;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Incident() {
    }

    public Incident(String incidentId, String source, String serviceId, String env,
                    String severity, String status, String title, String description,
                    Double errorRate, Integer errorCount, Instant windowStart, Instant windowEnd,
                    String traceId, String metadata) {
        this.incidentId = incidentId;
        this.source = source;
        this.serviceId = serviceId;
        this.env = env;
        this.severity = severity;
        this.status = status;
        this.title = title;
        this.description = description;
        this.errorRate = errorRate;
        this.errorCount = errorCount;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.traceId = traceId;
        this.metadata = metadata;
        this.detectedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markUpdated() {
        this.updatedAt = Instant.now();
    }

    public void resolve() {
        this.status = IncidentStatus.RESOLVED.name();
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public String getSource() {
        return source;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(Double errorRate) {
        this.errorRate = errorRate;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
