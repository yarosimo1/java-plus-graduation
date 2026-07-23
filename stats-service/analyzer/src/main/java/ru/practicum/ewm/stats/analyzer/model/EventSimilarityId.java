package ru.practicum.ewm.stats.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EventSimilarityId implements Serializable {
    private Long eventA;
    private Long eventB;
}
