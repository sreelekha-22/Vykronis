package io.vykronis.remediation.store;

import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency store for remediation executions. Implementations must persist
 * records so a restarted executor still treats an executed command as done.
 */
public interface RemediationRecordStore {

    Optional<RemediationRecord> findByCommandId(UUID commandId);

    void save(RemediationRecord record);
}