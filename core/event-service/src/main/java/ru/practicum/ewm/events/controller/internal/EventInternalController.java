package ru.practicum.ewm.events.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.events.dto.EventFullDto;
import ru.practicum.ewm.events.dto.EventShortDto;
import ru.practicum.ewm.events.mapper.EventMapper;
import ru.practicum.ewm.events.repository.EventRepository;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/events")
public class EventInternalController {
    private final EventRepository eventRepository;

    @GetMapping("/{eventId}")
    public EventFullDto getEvent(@PathVariable("eventId") Long eventId) {
        return eventRepository.findById(eventId)
                .map(EventMapper::toEventFullDto)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    @GetMapping
    public List<EventShortDto> getEvents(@RequestParam("ids") List<Long> ids) {
        return eventRepository.findAllByIdIn(ids).stream()
                .map(EventMapper::toEventShortDto)
                .toList();
    }

    @PatchMapping("/{eventId}/confirmed-requests")
    public EventFullDto updateConfirmedRequests(@PathVariable("eventId") Long eventId,
                                                @RequestParam("confirmedRequests") Integer confirmedRequests) {
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        event.setConfirmedRequests(confirmedRequests);
        return EventMapper.toEventFullDto(eventRepository.save(event));
    }
}
