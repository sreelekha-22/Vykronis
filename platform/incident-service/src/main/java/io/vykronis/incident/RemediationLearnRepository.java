package io.vykronis.incident;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RemediationLearnRepository extends JpaRepository<RemediationLearn, Long> {

    boolean existsByIncidentId(String incidentId);
}