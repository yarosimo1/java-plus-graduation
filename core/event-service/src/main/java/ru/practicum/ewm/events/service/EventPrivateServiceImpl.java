package ru.practicum.ewm.events.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.categories.dto.CategoryDto;
import ru.practicum.ewm.categories.service.CategoryService;
import ru.practicum.ewm.client.AdminClient;
import ru.practicum.ewm.client.RequestsClient;
import ru.practicum.ewm.error.BadRequestException;
import ru.practicum.ewm.error.ConflictException;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.events.dto.*;
import ru.practicum.ewm.events.mapper.EventMapper;
import ru.practicum.ewm.events.model.Event;
import ru.practicum.ewm.events.model.EventState;
import ru.practicum.ewm.events.model.Location;
import ru.practicum.ewm.events.repository.EventRepository;
import ru.practicum.ewm.request.dto.RequestDto;
import ru.practicum.ewm.request.model.StatusRequest;
import ru.practicum.ewm.user.dto.UserDto;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventPrivateServiceImpl implements EventPrivateService {
    private final EventRepository eventRepository;
    private final AdminClient adminClient;
    private final RequestsClient requestsClient;
    private final CategoryService categoryService;

    @Override
    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        int page = from / size;
        Pageable pageable = PageRequest.of(page, size);

        Page<Event> events = eventRepository.findAllByInitiatorId(userId, pageable);

        return events.stream()
                .map(EventMapper::toEventShortDto)
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto addEvent(Long userId, NewEventDto newEventDto) {
        validateEventDate(newEventDto.getEventDate());

        UserDto initiator = adminClient.getUser(userId);
        CategoryDto category = categoryService.getById(newEventDto.getCategoryId());

        Event event = new Event();
        event.setTitle(newEventDto.getTitle());
        event.setAnnotation(newEventDto.getAnnotation());
        event.setDescription(newEventDto.getDescription());
        event.setInitiatorId(initiator.getId());
        event.setInitiatorName(initiator.getName());
        event.setCategoryId(category.getId());
        event.setCategoryName(category.getName());
        event.setPaid(newEventDto.getPaid() != null ? newEventDto.getPaid() : false);
        event.setParticipantLimit(newEventDto.getParticipantLimit() != null ? newEventDto.getParticipantLimit() : 0);
        event.setRequestModeration(newEventDto.getRequestModeration() != null ? newEventDto.getRequestModeration() : true);
        event.setEventDate(newEventDto.getEventDate());
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());
        event.setConfirmedRequests(0);
        event.setViews(0L);
        if (newEventDto.getLocation() != null) {
            Location loc = new Location();
            loc.setLat(newEventDto.getLocation().getLat());
            loc.setLon(newEventDto.getLocation().getLon());
            event.setLocation(loc);
        }

        return EventMapper.toEventFullDto(eventRepository.save(event));
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        Event event = eventRepository
                .findByIdAndInitiatorId(eventId, userId).orElseThrow(() ->
                        new NotFoundException(String.format("Event with id: %s was not found", eventId)));

        return EventMapper.toEventFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateUserEvent(Long userId, Long eventId, UpdateEventUserRequest request) {
        Event event = eventRepository
                .findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() ->
                        new NotFoundException(String.format("Event with id: %s was not found", eventId)));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        if (request.getEventDate() != null) {
            validateEventDate(request.getEventDate());
        }

        if (request.getCategoryId() != null) {
            CategoryDto category = categoryService.getById(request.getCategoryId());
            event.setCategoryId(category.getId());
            event.setCategoryName(category.getName());
        }

        EventMapper.updateEventFromDto(request, event);
        if (request.getLocation() != null) {
            Location loc = new Location();
            loc.setLat(request.getLocation().getLat());
            loc.setLon(request.getLocation().getLon());
            event.setLocation(loc);
        }

        return EventMapper.toEventFullDto(eventRepository.save(event));
    }

    @Override
    public List<RequestDto> getEventRequests(Long userId, Long eventId) {
        Event event = eventRepository
                .findByIdAndInitiatorId(eventId, userId).orElseThrow(() ->
                        new NotFoundException(String.format("Event with id: %s was not found", eventId)));

        return requestsClient.getEventRequests(event.getId(), null);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(Long userId,
                                                              Long eventId,
                                                              EventRequestStatusUpdateRequest request) {
        Event event = getOwnedEvent(userId, eventId);

        if (isAutoConfirmationDisabled(event)) {
            return emptyResult();
        }

        List<RequestDto> requests = requestsClient.getEventRequests(event.getId(), request.getRequestIds());

        if (requests.size() != request.getRequestIds().size()) {
            throw new NotFoundException("One or more requests not found for event id=" + eventId);
        }

        validateRequests(requests);

        if (request.getStatus() == StatusRequest.CONFIRMED) {
            return processConfirmRequests(event, requests);
        } else {
            return processRejectRequests(requests);
        }
    }

    private Event getOwnedEvent(Long userId, Long eventId) {
        return eventRepository
                .findByIdAndInitiatorId(eventId, userId).orElseThrow(() ->
                        new NotFoundException(String.format("Event with id: %s was not found", eventId)));
    }

    private boolean isAutoConfirmationDisabled(Event event) {
        return event.getParticipantLimit() == 0 || !event.getRequestModeration();
    }

    private EventRequestStatusUpdateResult emptyResult() {
        return new EventRequestStatusUpdateResult(List.of(), List.of());
    }

    private void validateRequests(List<RequestDto> requests) {
        for (RequestDto pr : requests) {
            if (pr.getStatus() != StatusRequest.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
        }
    }

    private EventRequestStatusUpdateResult processConfirmRequests(Event event, List<RequestDto> requests) {
        int confirmedCount = event.getConfirmedRequests();
        int participantLimit = event.getParticipantLimit();

        // Если лимит уже достигнут
        if (confirmedCount >= participantLimit) {
            throw new ConflictException("The participant limit has been reached");
        }

        int freePlaces = participantLimit - confirmedCount;

        List<RequestDto> confirmed = List.of();
        List<RequestDto> rejected = List.of();

        if (requests.size() <= freePlaces) {
            // Все заявки помещаются
            confirmed = requestsClient.updateStatuses(
                    requests.stream()
                            .map(RequestDto::getId)
                            .toList(),
                    StatusRequest.CONFIRMED
            );

            confirmedCount += confirmed.size();
        } else {
            // Подтверждаем только столько, сколько осталось мест
            List<Long> confirmIds = requests.stream()
                    .limit(freePlaces)
                    .map(RequestDto::getId)
                    .toList();

            List<Long> rejectIds = requests.stream()
                    .skip(freePlaces)
                    .map(RequestDto::getId)
                    .toList();

            if (!confirmIds.isEmpty()) {
                confirmed = requestsClient.updateStatuses(confirmIds, StatusRequest.CONFIRMED);
                confirmedCount += confirmed.size();
            }

            if (!rejectIds.isEmpty()) {
                rejected = requestsClient.updateStatuses(rejectIds, StatusRequest.REJECTED);
            }
        }

        event.setConfirmedRequests(confirmedCount);
        eventRepository.save(event);

        // Если после подтверждения лимит достигнут,
        // отклоняем все оставшиеся PENDING заявки
        if (confirmedCount >= participantLimit) {
            List<RequestDto> pending = requestsClient.getEventRequests(event.getId(), null)
                    .stream()
                    .filter(r -> r.getStatus() == StatusRequest.PENDING)
                    .toList();

            if (!pending.isEmpty()) {
                List<RequestDto> autoRejected = requestsClient.updateStatuses(
                        pending.stream()
                                .map(RequestDto::getId)
                                .toList(),
                        StatusRequest.REJECTED
                );

                rejected = rejected.isEmpty()
                        ? autoRejected
                        : java.util.stream.Stream.concat(rejected.stream(), autoRejected.stream())
                          .toList();
            }
        }

        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }

    private EventRequestStatusUpdateResult processRejectRequests(List<RequestDto> requests) {
        List<RequestDto> rejected = requestsClient.updateStatuses(
                requests.stream().map(RequestDto::getId).toList(),
                StatusRequest.REJECTED
        );

        return buildResult(List.of(), rejected);
    }

    private EventRequestStatusUpdateResult buildResult(List<RequestDto> confirmed, List<RequestDto> rejected) {
        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }

    private void validateEventDate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new BadRequestException("Event date must be at least 2 hours in the future");
        }
    }
}
