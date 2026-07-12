package ru.practicum.request.model;

import jakarta.persistence.*;
import lombok.*;
import ru.practicum.ewm.request.model.StatusRequest;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "requester_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime created;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusRequest status;
}



