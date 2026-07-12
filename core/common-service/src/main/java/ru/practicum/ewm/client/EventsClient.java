package ru.practicum.ewm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.ewm.events.dto.EventFullDto;
import ru.practicum.ewm.events.dto.EventShortDto;

import java.util.List;

@FeignClient(name = "events-service", path = "/internal/events")
public interface EventsClient {
    @GetMapping("/{eventId}")
    EventFullDto getEvent(@PathVariable Long eventId);

    @GetMapping
    List<EventShortDto> getEvents(@RequestParam List<Long> ids);
}

