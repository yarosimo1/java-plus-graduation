package ru.practicum.ewm.stats.analyzer.controller;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.ewm.stats.analyzer.service.RecommendationService;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;

import java.util.Map;

@GrpcService
@RequiredArgsConstructor
public class RecommendationsGrpcService extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {
    private final RecommendationService recommendationService;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        recommendationService.getRecommendations(request.getUserId(), request.getMaxResults())
                .forEach(recommendation -> sendRecommendation(recommendation, responseObserver));
        responseObserver.onCompleted();
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        recommendationService.getSimilarEvents(request.getEventId(), request.getUserId(), request.getMaxResults())
                .forEach(recommendation -> sendRecommendation(recommendation, responseObserver));
        responseObserver.onCompleted();
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        recommendationService.getInteractionCounts(request.getEventIdList())
                .forEach(recommendation -> sendRecommendation(recommendation, responseObserver));
        responseObserver.onCompleted();
    }

    private void sendRecommendation(Map.Entry<Long, Double> recommendation,
                                    StreamObserver<RecommendedEventProto> responseObserver) {
        responseObserver.onNext(RecommendedEventProto.newBuilder()
                .setEventId(recommendation.getKey())
                .setScore(recommendation.getValue())
                .build());
    }
}
