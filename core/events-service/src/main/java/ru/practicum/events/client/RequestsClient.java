package ru.practicum.events.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.ewm.request.dto.RequestDto;
import ru.practicum.ewm.request.model.StatusRequest;

import java.util.List;

@FeignClient(name = "request-service", path = "/internal")
public interface RequestsClient {
    @GetMapping("/events/{eventId}/requests")
    List<RequestDto> getEventRequests(@PathVariable Long eventId, @RequestParam(required = false) List<Long> ids);

    @GetMapping("/events/{eventId}/requests/count")
    long countEventRequests(@PathVariable Long eventId, @RequestParam StatusRequest status);

    @PatchMapping("/requests/status")
    List<RequestDto> updateStatuses(@RequestBody List<Long> requestIds, @RequestParam StatusRequest status);
}
