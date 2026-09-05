package io.vykronis.policy.audit;

import io.vykronis.contracts.model.Env;
import io.vykronis.policy.PolicyAction;
import io.vykronis.policy.PolicyDecision;
import io.vykronis.policy.PolicySubject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory append-only audit log. Entries are immutable records chained by
 * SHA-256; the exposed list is an unmodifiable snapshot. Thread-safe for the
 * single-node dev loop.
 */
@Component
public class InMemoryAuditLog implements AuditLog {

    static final String GENESIS_PREVIOUS_HASH = "0";

    private final List<AuditEntry> entries = new ArrayList<>();
    private String lastHash = GENESIS_PREVIOUS_HASH;

    @Override
    public synchronized AuditEntry append(PolicyAction action, Env environment, PolicySubject subject,
            PolicyDecision decision) {
        long sequence = (long) entries.size() + 1;
        AuditEntry entry = AuditEntry.of(sequence, Instant.now(), action, environment, subject, decision, lastHash);
        entries.add(entry);
        lastHash = entry.hash();
        return entry;
    }

    @Override
    public synchronized List<AuditEntry> entries() {
        return List.copyOf(entries);
    }
}