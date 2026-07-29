package ru.practicum.ewm.stats.analyzer.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarityId;

import java.util.Collection;
import java.util.List;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, EventSimilarityId> {
    @Query("""
            select case
                       when similarity.eventA = :eventId then similarity.eventB
                       else similarity.eventA
                   end as eventId,
                   similarity.score as score
            from EventSimilarity similarity
            where (similarity.eventA = :eventId
                   and not exists (
                       select 1
                       from UserInteraction interaction
                       where interaction.userId = :userId
                         and interaction.eventId = similarity.eventB
                   ))
               or (similarity.eventB = :eventId
                   and not exists (
                       select 1
                       from UserInteraction interaction
                       where interaction.userId = :userId
                         and interaction.eventId = similarity.eventA
                   ))
            order by similarity.score desc
            """)
    List<SimilarEventRow> findSimilarCandidates(@Param("eventId") long eventId,
                                                @Param("userId") long userId,
                                                Pageable pageable);

    @Query("""
            select similarity
            from EventSimilarity similarity
            where similarity.eventA in :eventIds or similarity.eventB in :eventIds
            order by similarity.score desc
            """)
    List<EventSimilarity> findForEvents(@Param("eventIds") Collection<Long> eventIds);

    interface SimilarEventRow {
        Long getEventId();

        Double getScore();
    }
}
