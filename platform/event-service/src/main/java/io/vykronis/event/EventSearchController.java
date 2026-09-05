package io.vykronis.event;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Agent-facing search API. Searches indexed observability evidence (logs /
 * traces / metrics) within an incident's time window.
 */
@RestController
@RequestMapping("/api/search")
public class EventSearchController {

    private final EventSearchService searchService;

    public EventSearchController(EventSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/events")
    public List<EventSearchService.SearchHit> search(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String traceId) {
        Instant start = parseInstant("from", from);
        Instant end = parseInstant("to", to);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from must not be later than to");
        }
        return searchService.search(start, end, serviceId, type, traceId);
    }

    private Instant parseInstant(String name, String value) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
    }
}