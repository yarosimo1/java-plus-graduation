package ru.practicum.ewm.stats.analyzer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.model.UserInteraction;
import ru.practicum.ewm.stats.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.analyzer.repository.UserInteractionRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final UserInteractionRepository interactionRepository;
    private final EventSimilarityRepository similarityRepository;

    public List<Map.Entry<Long, Double>> getSimilarEvents(long eventId, long userId, int maxResults) {
        if (maxResults <= 0) {
            return List.of();
        }

        return similarityRepository.findSimilarCandidates(eventId, userId, PageRequest.of(0, maxResults))
                .stream()
                .map(row -> Map.entry(row.getEventId(), row.getScore()))
                .toList();
    }

    public List<Map.Entry<Long, Double>> getRecommendations(long userId, int maxResults) {
        List<UserInteraction> recentInteractions = interactionRepository.findTop50ByUserIdOrderByUpdatedAtDesc(userId);
        if (recentInteractions.isEmpty()) {
            return List.of();
        }

        Set<Long> interactedEventIds = recentInteractions.stream()
                .map(UserInteraction::getEventId)
                .collect(Collectors.toSet());
        Map<Long, Double> bestScoresByEvent = new HashMap<>();

        for (EventSimilarity similarity : similarityRepository.findForEvents(interactedEventIds)) {
            long candidateEventId = getCandidateEventId(similarity, interactedEventIds);
            if (!interactedEventIds.contains(candidateEventId)) {
                bestScoresByEvent.merge(candidateEventId, similarity.getScore(), Math::max);
            }
        }

        return bestScoresByEvent.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .toList();
    }

    public List<Map.Entry<Long, Double>> getInteractionCounts(List<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Double> scoresByEvent = interactionRepository.sumWeights(eventIds, PageRequest.of(0, eventIds.size())).stream()
                .collect(Collectors.toMap(
                        UserInteractionRepository.ScoreRow::getEventId,
                        UserInteractionRepository.ScoreRow::getScore
                ));

        return eventIds.stream()
                .map(eventId -> Map.entry(eventId, scoresByEvent.getOrDefault(eventId, 0.0)))
                .toList();
    }

    private long getCandidateEventId(EventSimilarity similarity, Set<Long> interactedEventIds) {
        return interactedEventIds.contains(similarity.getEventA()) ? similarity.getEventB() : similarity.getEventA();
    }
}
