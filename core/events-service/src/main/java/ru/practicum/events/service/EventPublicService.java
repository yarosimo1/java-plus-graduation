package ru.practicum.events.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.events.model.EventSort;
import ru.practicum.ewm.events.dto.EventFullDto;
import ru.practicum.ewm.events.dto.EventShortDto;

import java.time.LocalDateTime;
import java.util.List;

public interface EventPublicService {
    List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                        LocalDateTime rangeStart, LocalDateTime rangeEnd, Boolean onlyAvailable,
                                        EventSort sort, int from, int size, HttpServletRequest httpRequest);

    EventFullDto getPublicEventById(Long eventId, HttpServletRequest httpRequest);
}
