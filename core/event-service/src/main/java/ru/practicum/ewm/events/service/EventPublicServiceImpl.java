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
import ru.practicum.client.ActionType;
import ru.practicum.client.AnalyzerClient;
import ru.practicum.client.CollectorClient;
import ru.practicum.ewm.error.BadRequestException;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.events.dto.EventFullDto;
import ru.practicum.ewm.events.dto.EventShortDto;
import ru.practicum.ewm.events.mapper.EventMapper;
import ru.practicum.ewm.events.model.Event;
import ru.practicum.ewm.events.model.EventSort;
import ru.practicum.ewm.events.model.EventState;
import ru.practicum.ewm.events.repository.EventRepository;
import ru.practicum.ewm.events.repository.EventSpecification;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventPublicServiceImpl implements EventPublicService {
    private final EventRepository eventRepository;
    private final CollectorClient collectorClient;
    private final AnalyzerClient analyzerClient;

    @Override
    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd, Boolean onlyAvailable,
                                               EventSort sort, int from, int size, HttpServletRequest httpRequest) {

        if (rangeStart != null && rangeEnd != null && rangeEnd.isBefore(rangeStart)) {
            throw new BadRequestException("rangeEnd must not be before rangeStart");
        }

        Pageable pageable = buildPageable(sort, from, size);
        LocalDateTime effectiveStart = rangeStart != null ? rangeStart : LocalDateTime.now();

        Specification<Event> specification = buildPublicEventsSpecification(
                text,
                categories,
                paid,
                effectiveStart,
                rangeEnd,
                onlyAvailable
        );

        List<Event> events = eventRepository.findAll(specification, pageable).getContent();
        Map<Long, Double> ratings = getRatings(events.stream().map(Event::getId).toList());

        List<EventShortDto> result = events.stream()
                .map(event -> toShortDtoWithRating(event, ratings))
                .collect(Collectors.toList());

        if (sort == EventSort.VIEWS) {
            result.sort(Comparator.comparingDouble(EventShortDto::getRating).reversed());
        }

        return result;
    }

    @Override
    @Transactional
    public EventFullDto getPublicEventById(Long eventId, Long userId, HttpServletRequest httpRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }

        if (userId != null) {
            collectSafely(userId, eventId, ActionType.VIEW);
        }

        double rating = getRatings(List.of(eventId))
                .getOrDefault(eventId, getStoredRating(event));
        event.setRating(rating);
        eventRepository.save(event);

        EventFullDto dto = EventMapper.toEventFullDto(event);
        dto.setRating(rating);
        return dto;
    }

    @Override
    public List<EventShortDto> getRecommendations(long userId, int maxResults) {
        List<Long> eventIds = analyzerClient.getRecommendationsForUser(userId, maxResults)
                .map(RecommendedEventProto::getEventId)
                .toList();

        return eventRepository.findAllById(eventIds).stream()
                .map(EventMapper::toEventShortDto)
                .toList();
    }

    @Override
    public void likeEvent(long eventId, long userId) {
        boolean eventHasInteractions = analyzerClient.getInteractionsCount(List.of(eventId))
                .findFirst()
                .map(RecommendedEventProto::getScore)
                .orElse(0.0) > 0.0;

        if (!eventHasInteractions) {
            throw new BadRequestException("Only visited events can be liked");
        }

        collectorClient.collect(userId, eventId, ActionType.LIKE);
    }

    private Pageable buildPageable(EventSort sort, int from, int size) {
        int page = from / size;

        if (sort == EventSort.EVENT_DATE) {
            return PageRequest.of(page, size, Sort.by("eventDate").ascending());
        }

        return PageRequest.of(page, size);
    }

    private Specification<Event> buildPublicEventsSpecification(String text, List<Long> categories, Boolean paid,
                                                                LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                                Boolean onlyAvailable) {
        return Specification
                .where(EventSpecification.hasState(EventState.PUBLISHED))
                .and(EventSpecification.hasText(text))
                .and(EventSpecification.hasCategories(categories))
                .and(EventSpecification.hasPaid(paid))
                .and(EventSpecification.eventDateAfter(rangeStart))
                .and(EventSpecification.eventDateBefore(rangeEnd))
                .and(EventSpecification.isAvailable(onlyAvailable));
    }

    private EventShortDto toShortDtoWithRating(Event event, Map<Long, Double> ratings) {
        EventShortDto dto = EventMapper.toEventShortDto(event);
        dto.setRating(ratings.getOrDefault(event.getId(), getStoredRating(event)));
        return dto;
    }

    private double getStoredRating(Event event) {
        return event.getRating() == null ? 0.0 : event.getRating();
    }

    private Map<Long, Double> getRatings(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        try {
            return analyzerClient.getInteractionsCount(eventIds)
                    .collect(Collectors.toMap(
                            RecommendedEventProto::getEventId,
                            RecommendedEventProto::getScore
                    ));
        } catch (Exception e) {
            log.warn("Failed to fetch ratings for eventIds={}: {}", eventIds, e.getMessage(), e);
            return Map.of();
        }
    }

    private void collectSafely(long userId, long eventId, ActionType actionType) {
        try {
            collectorClient.collect(userId, eventId, actionType);
        } catch (Exception e) {
            log.warn(
                    "Failed to send {} action to collector for userId={}, eventId={}: {}",
                    actionType,
                    userId,
                    eventId,
                    e.getMessage(),
                    e
            );
        }
    }
}
