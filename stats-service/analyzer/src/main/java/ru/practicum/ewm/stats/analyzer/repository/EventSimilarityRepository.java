package ru.practicum.ewm.stats.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarity;
import ru.practicum.ewm.stats.analyzer.model.EventSimilarityId;

import java.util.Collection;
import java.util.List;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, EventSimilarityId> {
    @Query("""
            select similarity
            from EventSimilarity similarity
            where similarity.eventA = :eventId or similarity.eventB = :eventId
            order by similarity.score desc
            """)
    List<EventSimilarity> findForEvent(@Param("eventId") long eventId);

    @Query("""
            select similarity
            from EventSimilarity similarity
            where similarity.eventA in :eventIds or similarity.eventB in :eventIds
            order by similarity.score desc
            """)
    List<EventSimilarity> findForEvents(@Param("eventIds") Collection<Long> eventIds);
}
