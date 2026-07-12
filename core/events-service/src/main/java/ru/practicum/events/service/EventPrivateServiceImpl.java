package ru.practicum.events.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.events.client.AdminClient;
import ru.practicum.events.client.CommonClient;
import ru.practicum.events.client.RequestsClient;
import ru.practicum.events.mapper.EventMapper;
import ru.practicum.events.model.Event;
import ru.practicum.events.repository.EventRepository;
import ru.practicum.ewm.categories.dto.CategoryDto;
import ru.practicum.ewm.error.BadRequestException;
import ru.practicum.ewm.error.ConflictException;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.events.dto.*;
import ru.practicum.ewm.events.model.EventState;
import ru.practicum.ewm.request.dto.RequestDto;
import ru.practicum.ewm.request.model.StatusRequest;
import ru.practicum.ewm.user.dto.UserDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventPrivateServiceImpl implements EventPrivateService {
    private final EventRepository eventRepository;
    private final AdminClient adminClient;
    private final CommonClient commonClient;
    private final RequestsClient requestsClient;

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
        CategoryDto category = commonClient.getCategory(newEventDto.getCategoryId());

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
            ru.practicum.events.model.Location loc = new ru.practicum.events.model.Location();
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
            CategoryDto category = commonClient.getCategory(request.getCategoryId());
            event.setCategoryId(category.getId());
            event.setCategoryName(category.getName());
        }

        EventMapper.updateEventFromDto(request, event);
        if (request.getLocation() != null) {
            ru.practicum.events.model.Location loc = new ru.practicum.events.model.Location();
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
        int limit = event.getParticipantLimit();

        if (confirmedCount >= limit) {
            throw new ConflictException("The participant limit has been reached");
        }

        List<RequestDto> confirmed = requestsClient.updateStatuses(requests.stream().map(RequestDto::getId).toList(), StatusRequest.CONFIRMED);
        List<RequestDto> rejected = List.of();

        confirmedCount += requests.size();
        if (confirmedCount > limit) {
            throw new ConflictException("The participant limit has been reached");
        }

        event.setConfirmedRequests(confirmedCount);

        eventRepository.save(event);

        if (event.getConfirmedRequests() >= event.getParticipantLimit()) {
            List<RequestDto> pending = requestsClient.getEventRequests(event.getId(), null).stream()
                    .filter(r -> r.getStatus() == StatusRequest.PENDING)
                    .toList();
            rejected = pending.isEmpty()
                    ? List.of()
                    : requestsClient.updateStatuses(pending.stream().map(RequestDto::getId).toList(), StatusRequest.REJECTED);
        }

        return buildResult(confirmed, rejected);
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
