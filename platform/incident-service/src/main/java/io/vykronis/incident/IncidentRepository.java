package io.vykronis.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentId(String incidentId);

    List<Incident> findAllByOrderByDetectedAtDesc();

    List<Incident> findByServiceIdOrderByDetectedAtDesc(String serviceId);

    List<Incident> findByStatusOrderByDetectedAtDesc(String status);

    boolean existsByIncidentId(String incidentId);
}
