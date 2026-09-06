package io.vykronis.remediation.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RemediationRecordRepository extends JpaRepository<RemediationRecordEntity, UUID> {

    Optional<RemediationRecordEntity> findByCommandId(UUID commandId);
}