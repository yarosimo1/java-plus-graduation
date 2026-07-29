package ru.practicum.ewm.stats.analyzer.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.stats.analyzer.model.UserInteraction;
import ru.practicum.ewm.stats.analyzer.model.UserInteractionId;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface UserInteractionRepository extends JpaRepository<UserInteraction, UserInteractionId> {
    @Query("select interaction.eventId from UserInteraction interaction where interaction.userId = :userId")
    Set<Long> findEventIdsByUserId(@Param("userId") long userId);

    List<UserInteraction> findTop50ByUserIdOrderByUpdatedAtDesc(long userId);

    @Query("""
            select interaction.eventId as eventId, sum(interaction.weight) as score
            from UserInteraction interaction
            where interaction.eventId in :eventIds
            group by interaction.eventId
            """)
    List<ScoreRow> sumWeights(@Param("eventIds") Collection<Long> eventIds, Pageable pageable);

    interface ScoreRow {
        Long getEventId();

        Double getScore();
    }
}
