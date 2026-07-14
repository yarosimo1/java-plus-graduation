package ru.practicum.ewm.events.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.ewm.client.StatsClient;
import ru.practicum.ewm.events.mapper.EventMapper;
import ru.practicum.ewm.events.model.Event;
import ru.practicum.ewm.events.model.EventSort;
import ru.practicum.ewm.events.repository.EventRepository;
import ru.practicum.ewm.events.repository.EventSpecification;
import ru.practicum.ewm.error.BadRequestException;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.events.dto.EventFullDto;
import ru.practicum.ewm.events.dto.EventShortDto;
import ru.practicum.ewm.events.model.EventState;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventPublicServiceImpl implements EventPublicService {
    private final EventRepository eventRepository;
    private final StatsClient statsClient;
    private final ConcurrentMap<Long, Set<String>> localUniqueViews = new ConcurrentHashMap<>();

    @Override
    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd, Boolean onlyAvailable,
                                               EventSort sort, int from, int size, HttpServletRequest httpRequest) {
        int page = from / size;

        Pageable pageable = (sort == EventSort.EVENT_DATE)
                ? PageRequest.of(page, size, Sort.by("eventDate").ascending())
                : PageRequest.of(page, size);

        if (rangeStart != null && rangeEnd != null && rangeEnd.isBefore(rangeStart)) {
            throw new BadRequestException("rangeEnd must not be before rangeStart");
        }

        LocalDateTime effectiveStart = rangeStart != null ? rangeStart : LocalDateTime.now();

        Specification<Event> spec = Specification
                .where(EventSpecification.hasState(EventState.PUBLISHED))
                .and(EventSpecification.hasText(text))
                .and(EventSpecification.hasCategories(categories))
                .and(EventSpecification.hasPaid(paid))
                .and(EventSpecification.eventDateAfter(effectiveStart))
                .and(EventSpecification.eventDateBefore(rangeEnd))
                .and(EventSpecification.isAvailable(onlyAvailable));

        List<Event> events = eventRepository.findAll(spec, pageable).getContent();

        saveHit(httpRequest, getClientIp(httpRequest));

        Map<Long, Long> viewsMap = getViewsMap(events);

        List<EventShortDto> result = events.stream()
                .map(e -> {
                    EventShortDto dto = EventMapper.toEventShortDto(e);
                    dto.setViews(viewsMap.getOrDefault(e.getId(), e.getViews() == null ? 0L : e.getViews()));
                    return dto;
                })
                .collect(Collectors.toList());

        if (sort == EventSort.VIEWS) {
            result.sort(Comparator.comparingLong(EventShortDto::getViews).reversed());
        }

        return result;
    }

    @Override
    @Transactional
    public EventFullDto getPublicEventById(Long eventId, HttpServletRequest httpRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        String clientIp = getClientIp(httpRequest);
        saveHit(httpRequest, clientIp);

        long views = registerLocalUniqueView(event, clientIp);

        try {
            List<ViewStatsDto> stats = statsClient.getStats(
                    LocalDateTime.now().minusYears(100),
                    LocalDateTime.now().plusDays(3),
                    List.of("/events/" + eventId),
                    true);

            if (!stats.isEmpty()) {
                views = Math.max(views, stats.getFirst().getHits());
                event.setViews(views);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch view stats for event id={}: {}", eventId, e.getMessage(), e);
        }

        eventRepository.save(event);

        EventFullDto dto = EventMapper.toEventFullDto(event);
        dto.setViews(views);
        return dto;
    }

    private long registerLocalUniqueView(Event event, String clientIp) {
        Set<String> eventIps = localUniqueViews.computeIfAbsent(event.getId(), id -> ConcurrentHashMap.newKeySet());
        eventIps.add(clientIp);
        long views = eventIps.size();
        event.setViews(views);
        return views;
    }


    private void saveHit(HttpServletRequest request, String clientIp) {
        try {
            statsClient.saveHit(new EndpointHitDto(
                    null,
                    "ewm-main-service",
                    request.getRequestURI(),
                    clientIp,
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            ));
        } catch (Exception e) {
            log.warn("Failed to save hit for uri={} ip={}: {}", request.getRequestURI(), getClientIp(request),
                    e.getMessage(), e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private Map<Long, Long> getViewsMap(List<Event> events) {
        if (events.isEmpty()) return Map.of();
        try {
            List<String> uris = events.stream()
                    .map(e -> "/events/" + e.getId())
                    .toList();
            List<ViewStatsDto> stats = statsClient.getStats(
                    LocalDateTime.now().minusMonths(2),
                    LocalDateTime.now().plusMonths(2),
                    uris,
                    true
            );
            return stats.stream()
                    .collect(Collectors.toMap(
                            s -> Long.parseLong(s.getUri().replace("/events/", "")),
                            ViewStatsDto::getHits
                    ));
        } catch (Exception e) {
            log.warn("Failed to fetch view stats for {} uris: {}", events.size(), e.getMessage(), e);
            return Map.of();
        }
    }
}
