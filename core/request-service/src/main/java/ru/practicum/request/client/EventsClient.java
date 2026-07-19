package ru.practicum.request.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.ewm.events.dto.EventFullDto;

@FeignClient(name = "event-service", path = "/internal/events")
public interface EventsClient {
    @GetMapping("/{eventId}")
    EventFullDto getEvent(@PathVariable("eventId") Long eventId);

    @PatchMapping("/{eventId}/confirmed-requests")
    EventFullDto updateConfirmedRequests(@PathVariable("eventId") Long eventId,
                                         @RequestParam("confirmedRequests") Integer confirmedRequests);
}
