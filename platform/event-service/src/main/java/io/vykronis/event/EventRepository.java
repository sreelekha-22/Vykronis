package io.vykronis.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Event findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    @Query("""
            select e from Event e
            where e.timestamp >= :from and e.timestamp <= :to
              and (coalesce(:serviceId, '') = '' or e.serviceId = :serviceId)
              and (coalesce(:type, '') = '' or e.type = :type)
              and (coalesce(:traceId, '') = '' or e.traceId = :traceId)
            order by e.timestamp asc""")
    List<Event> searchByWindow(@Param("from") Instant from,
                               @Param("to") Instant to,
                               @Param("serviceId") String serviceId,
                               @Param("type") String type,
                               @Param("traceId") String traceId,
                               Pageable pageable);
}