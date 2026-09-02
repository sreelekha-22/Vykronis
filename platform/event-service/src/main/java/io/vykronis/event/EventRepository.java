package io.vykronis.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    Event findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}