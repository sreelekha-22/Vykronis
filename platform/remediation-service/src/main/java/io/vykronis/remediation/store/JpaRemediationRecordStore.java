package io.vykronis.remediation.store;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link RemediationRecordStore} backed by Postgres so idempotency survives
 * service restarts (a replayed command id returns the stored result).
 */
@Component
public class JpaRemediationRecordStore implements RemediationRecordStore {

    private final RemediationRecordRepository repository;

    public JpaRemediationRecordStore(RemediationRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RemediationRecord> findByCommandId(UUID commandId) {
        return repository.findByCommandId(commandId).map(RemediationRecordEntity::toRecord);
    }

    @Override
    public void save(RemediationRecord record) {
        repository.save(new RemediationRecordEntity(record));
    }
}