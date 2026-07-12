package ru.practicum.request.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.request.dto.RequestDto;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.ewm.request.model.StatusRequest;
import ru.practicum.request.repository.RequestRepository;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class RequestInternalController {
    private final RequestRepository requestRepository;

    @GetMapping("/events/{eventId}/requests")
    public List<RequestDto> getEventRequests(@PathVariable Long eventId,
                                             @RequestParam(required = false) List<Long> ids) {
        return (ids == null || ids.isEmpty()
                ? requestRepository.findAllByEventId(eventId)
                : requestRepository.findAllByIdInAndEventId(ids, eventId)).stream()
                .map(RequestMapper::toRequestDto)
                .toList();
    }

    @GetMapping("/events/{eventId}/requests/count")
    public long countEventRequests(@PathVariable Long eventId, @RequestParam StatusRequest status) {
        return requestRepository.countByEventIdAndStatus(eventId, status);
    }

    @PatchMapping("/requests/status")
    public List<RequestDto> updateStatuses(@RequestBody List<Long> requestIds, @RequestParam StatusRequest status) {
        return requestRepository.findAllById(requestIds).stream()
                .peek(request -> request.setStatus(status))
                .map(requestRepository::save)
                .map(RequestMapper::toRequestDto)
                .toList();
    }
}
