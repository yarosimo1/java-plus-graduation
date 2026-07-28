package ru.practicum.ewm.stats.aggregator;

import lombok.RequiredArgsConstructor;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserActionListener {
    private static final String EVENTS_SIMILARITY_TOPIC = "stats.events-similarity.v1";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final Map<Long, Map<Long, Double>> weightsByEventAndUser = new HashMap<>();
    private final Map<Long, Double> weightSumsByEvent = new HashMap<>();
    private final Map<Long, Map<Long, Double>> minWeightSumsByEventPair = new HashMap<>();

    @KafkaListener(topics = "stats.user-actions.v1")
    public synchronized void onUserAction(byte[] payload) {
        UserActionAvro action = read(payload, UserActionAvro.class);
        double newWeight = getWeight(action.getActionType());
        Map<Long, Double> weightsByUser = weightsByEventAndUser.computeIfAbsent(
                action.getEventId(),
                eventId -> new HashMap<>()
        );
        double previousWeight = weightsByUser.getOrDefault(action.getUserId(), 0.0);

        if (newWeight <= previousWeight) {
            return;
        }

        weightsByUser.put(action.getUserId(), newWeight);
        weightSumsByEvent.merge(action.getEventId(), newWeight - previousWeight, Double::sum);
        recalculateSimilarities(action, previousWeight, newWeight);
    }

    private void recalculateSimilarities(UserActionAvro action, double previousWeight, double newWeight) {
        for (Long otherEventId : weightsByEventAndUser.keySet()) {
            if (otherEventId.equals(action.getEventId())) {
                continue;
            }

            double otherWeight = weightsByEventAndUser.get(otherEventId).getOrDefault(action.getUserId(), 0.0);
            if (otherWeight == 0.0) {
                continue;
            }

            double delta = Math.min(newWeight, otherWeight) - Math.min(previousWeight, otherWeight);

            updateSimilarity(action, otherEventId, delta);
        }
    }

    private void updateSimilarity(UserActionAvro action, long otherEventId, double minWeightDelta) {
        long eventId = action.getEventId();
        double minWeightSum = getMinWeightSum(eventId, otherEventId) + minWeightDelta;
        putMinWeightSum(eventId, otherEventId, minWeightSum);

        double score = minWeightSum / Math.sqrt(weightSumsByEvent.get(eventId) * weightSumsByEvent.get(otherEventId));
        long firstEventId = Math.min(eventId, otherEventId);
        long secondEventId = Math.max(eventId, otherEventId);

        EventSimilarityAvro similarity = EventSimilarityAvro.newBuilder()
                .setEventA(firstEventId)
                .setEventB(secondEventId)
                .setScore(score)
                .setTimestamp((Instant) action.getTimestamp())
                .build();

        kafkaTemplate.send(
                EVENTS_SIMILARITY_TOPIC,
                firstEventId + ":" + secondEventId,
                toBytes(similarity)
        );
    }

    private double getWeight(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }

    private void putMinWeightSum(long eventA, long eventB, double value) {
        long firstEventId = Math.min(eventA, eventB);
        long secondEventId = Math.max(eventA, eventB);

        minWeightSumsByEventPair
                .computeIfAbsent(firstEventId, eventId -> new HashMap<>())
                .put(secondEventId, value);
    }

    private double getMinWeightSum(long eventA, long eventB) {
        long firstEventId = Math.min(eventA, eventB);
        long secondEventId = Math.max(eventA, eventB);

        return minWeightSumsByEventPair
                .getOrDefault(firstEventId, Map.of())
                .getOrDefault(secondEventId, 0.0);
    }

    private <T> T read(byte[] payload, Class<T> clazz) {
        try {
            Decoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
            return new SpecificDatumReader<>(clazz).read(null, decoder);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize Avro message", e);
        }
    }

    private byte[] toBytes(EventSimilarityAvro similarity) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Encoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new SpecificDatumWriter<>(EventSimilarityAvro.class).write(similarity, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize event similarity", e);
        }
    }
}
