package ru.practicum.ewm.stats.analyzer.service;

import lombok.RequiredArgsConstructor;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.model.UserInteraction;
import ru.practicum.ewm.stats.analyzer.model.UserInteractionId;
import ru.practicum.ewm.stats.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.analyzer.repository.UserInteractionRepository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class StatsKafkaListener {
    private final UserInteractionRepository interactionRepository;
    private final EventSimilarityRepository similarityRepository;

    @Transactional
    @KafkaListener(topics = "stats.user-actions.v1")
    public void onUserAction(byte[] payload) {
        UserActionAvro action = read(payload, UserActionAvro.class);
        double weight = getWeight(action);
        UserInteractionId id = new UserInteractionId(action.getUserId(), action.getEventId());
        UserInteraction interaction = interactionRepository.findById(id)
                .orElseGet(() -> UserInteraction.builder()
                        .userId(action.getUserId())
                        .eventId(action.getEventId())
                        .weight(0.0)
                        .build());

        if (weight <= interaction.getWeight()) {
            return;
        }

        interaction.setWeight(weight);
        interaction.setUpdatedAt(toLocalDateTime((Instant) action.getTimestamp()));
        interactionRepository.save(interaction);
    }

    @Transactional
    @KafkaListener(topics = "stats.events-similarity.v1")
    public void onEventSimilarity(byte[] payload) {
        EventSimilarityAvro similarity = read(payload, EventSimilarityAvro.class);

        similarityRepository.save(EventSimilarity.builder()
                .eventA(similarity.getEventA())
                .eventB(similarity.getEventB())
                .score(similarity.getScore())
                .updatedAt(toLocalDateTime((Instant) similarity.getTimestamp()))
                .build());
    }

    private double getWeight(UserActionAvro action) {
        return switch (action.getActionType()) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }

    private LocalDateTime toLocalDateTime(Instant timestamp) {
        return LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC);
    }

    private <T> T read(byte[] payload, Class<T> clazz) {
        try {
            Decoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
            return new SpecificDatumReader<>(clazz).read(null, decoder);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize Avro message", e);
        }
    }
}
