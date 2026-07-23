package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.error.BadRequestException;
import ru.practicum.ewm.error.ConflictException;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.events.dto.EventFullDto;
import ru.practicum.ewm.events.model.EventState;
import ru.practicum.ewm.request.dto.RequestDto;
import ru.practicum.ewm.request.model.StatusRequest;
import ru.practicum.request.client.AdminClient;
import ru.practicum.request.client.EventsClient;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.request.model.Request;
import ru.practicum.request.repository.RequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {
    private final RequestRepository requestRepository;
    private final AdminClient adminClient;
    private final EventsClient eventsClient;
    private final ru.practicum.client.CollectorClient collectorClient;

    @Override
    @Transactional
    public RequestDto addUserRequest(long userId, long eventId) {
        log.info("userId={}, eventId={}", userId, eventId);
        if (eventId <= 0) {
            throw new BadRequestException("eventId must be greater than 0");
        }

        log.info("Save request");

        adminClient.getUser(userId);
        EventFullDto event = eventsClient.getEvent(eventId);

        if (event.getInitiator() != null && event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Initiator cannot request participation in own event");
        }

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        boolean exists = requestRepository.existsByRequesterIdAndEventId(userId, eventId);

        if (exists) {
            throw new ConflictException("Participation request already exists");
        }

        if (event.getParticipantLimit() > 0) {
            long confirmed = requestRepository.countByEventIdAndStatus(eventId, StatusRequest.CONFIRMED);
            if (confirmed >= event.getParticipantLimit()) {
                throw new ConflictException("Participant limit has been reached");
            }
        }

        StatusRequest statusRequest = (!event.getRequestModeration() || event.getParticipantLimit() == 0)
                ? StatusRequest.CONFIRMED
                : StatusRequest.PENDING;

        Request request = Request.builder()
                .requesterId(userId)
                .status(statusRequest)
                .created(LocalDateTime.now())
                .eventId(eventId)
                .build();

        Request saved = Objects.requireNonNull(requestRepository.save(request));
        RequestDto result = RequestMapper.toRequestDto(saved);

        if (statusRequest == StatusRequest.CONFIRMED) {
            eventsClient.updateConfirmedRequests(eventId, event.getConfirmedRequests() + 1);
        }

        collectorClient.collect(userId, eventId, ru.practicum.client.ActionType.REGISTER);

        return result;
    }

    @Override
    @Transactional
    public RequestDto cancelRequest(long requesterId, long requestId) {
        Request request = requestRepository.findById(requestId).orElseThrow(
                () -> new NotFoundException(String.format("Request with id: %s was not found", requestId)));

        if (!request.getRequesterId().equals(requesterId)) {
            throw new NotFoundException(String.format("Requester with id: %s was not found", requesterId));
        }

        boolean wasConfirmed = request.getStatus() == StatusRequest.CONFIRMED;
        request.setStatus(StatusRequest.CANCELED);

        if (wasConfirmed) {
            EventFullDto event = eventsClient.getEvent(request.getEventId());
            eventsClient.updateConfirmedRequests(request.getEventId(), Math.max(0, event.getConfirmedRequests() - 1));
        }

        Request updated = requestRepository.save(request);

        return RequestMapper.toRequestDto(updated);
    }

    @Override
    public List<RequestDto> getUserRequests(long requesterId) {
        adminClient.getUser(requesterId);

        return requestRepository.findAllByRequesterId(requesterId).stream()
                .map(RequestMapper::toRequestDto)
                .toList();
    }
}
