package ru.practicum.ewm.stats.collector;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.springframework.kafka.core.KafkaTemplate;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionProto;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

@GrpcService
@RequiredArgsConstructor
public class UserActionGrpcService extends UserActionControllerGrpc.UserActionControllerImplBase {
    private static final String USER_ACTIONS_TOPIC = "stats.user-actions.v1";

    private final KafkaTemplate<Long, byte[]> kafkaTemplate;

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        UserActionAvro userAction = toAvro(request);
        kafkaTemplate.send(USER_ACTIONS_TOPIC, request.getEventId(), toBytes(userAction));

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private UserActionAvro toAvro(UserActionProto request) {
        return UserActionAvro.newBuilder()
                .setUserId(request.getUserId())
                .setEventId(request.getEventId())
                .setActionType(ActionTypeAvro.valueOf(request.getActionType().name().replace("ACTION_", "")))
                .setTimestamp(Instant.ofEpochSecond(
                        request.getTimestamp().getSeconds(),
                        request.getTimestamp().getNanos()
                ))
                .build();
    }

    private byte[] toBytes(UserActionAvro action) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Encoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new SpecificDatumWriter<>(UserActionAvro.class).write(action, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize user action", e);
        }
    }
}
