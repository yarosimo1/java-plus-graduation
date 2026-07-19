package ru.practicum.ewm.comments.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.client.AdminClient;
import ru.practicum.ewm.comments.dto.CommentDto;
import ru.practicum.ewm.comments.dto.NewCommentDto;
import ru.practicum.ewm.comments.mapper.CommentMapper;
import ru.practicum.ewm.comments.model.Comment;
import ru.practicum.ewm.comments.model.CommentLike;
import ru.practicum.ewm.comments.model.Sort;
import ru.practicum.ewm.comments.repository.CommentLikeRepository;
import ru.practicum.ewm.comments.repository.CommentRepository;
import ru.practicum.ewm.common.OffsetPageRequest;
import ru.practicum.ewm.error.ConflictException;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.events.dto.EventFullDto;
import ru.practicum.ewm.events.dto.EventShortDto;
import ru.practicum.ewm.events.mapper.EventMapper;
import ru.practicum.ewm.events.model.EventState;
import ru.practicum.ewm.events.repository.EventRepository;
import ru.practicum.ewm.user.dto.UserShortDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {
    private final AdminClient adminClient;
    private final EventRepository eventRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;

    @Override
    public CommentDto createComment(Long userId, Long eventId, NewCommentDto newCommentDto) {
        UserShortDto author = CommentMapper.toShort(adminClient.getUser(userId));
        EventFullDto event = getEvent(eventId);

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Comments are only allowed on published events.");
        }

        Comment comment = commentRepository.save(CommentMapper.toComment(newCommentDto, userId, eventId));
        log.debug("Comment created with id: {}", comment.getId());

        return CommentMapper.toCommentDto(comment, author, EventMapper.toEventShortDto(event), 0L);
    }

    @Override
    public CommentDto updateComment(Long userId, Long commentId, NewCommentDto newCommentDto) {
        UserShortDto author = CommentMapper.toShort(adminClient.getUser(userId));
        Comment comment = checkAndGetComment(commentId);
        log.info("Updating comment for user: {}, commentId: {}", userId, commentId);

        if (!comment.getAuthorId().equals(userId)) {
            throw new ConflictException("Only the author can edit the comment.");
        }

        comment.setText(newCommentDto.getText());
        comment.setEdited(LocalDateTime.now());
        long countLikes = commentLikeRepository.countByCommentId(comment.getId());

        return CommentMapper.toCommentDto(comment, author, EventMapper.toEventShortDto(getEvent(comment.getEventId())), countLikes);

    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getCommentsByAuthorId(Long userId, Integer from, Integer size, Sort sort) {
        UserShortDto userShort = CommentMapper.toShort(adminClient.getUser(userId));

        Pageable pageable = new OffsetPageRequest(from, size);

        Page<Comment> page = switch (sort) {
            case ASC -> commentRepository.findAllByAuthorIdOrderByLikesAsc(userId, pageable);
            case DESC -> commentRepository.findAllByAuthorIdOrderByLikesDesc(userId, pageable);
        };

        return mapComments(page.getContent(), userShort, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getCommentsByEventId(Long eventId, Integer from, Integer size, Sort sort) {
        EventShortDto eventShort = EventMapper.toEventShortDto(getEvent(eventId));

        Pageable pageable = new OffsetPageRequest(from, size);

        Page<Comment> page = switch (sort) {
            case ASC -> commentRepository.findAllByEventIdOrderByLikesAsc(eventId, pageable);
            case DESC -> commentRepository.findAllByEventIdOrderByLikesDesc(eventId, pageable);
        };

        return mapComments(page.getContent(), null, eventShort);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentDto getCommentById(Long commentId) {
        Comment comment = checkAndGetComment(commentId);
        long countLikes = commentLikeRepository.countByCommentId(comment.getId());

        return CommentMapper.toCommentDto(comment, CommentMapper.toShort(adminClient.getUser(comment.getAuthorId())),
                EventMapper.toEventShortDto(getEvent(comment.getEventId())), countLikes);
    }

    @Override
    public void deleteComment(Long userId, Long commentId) {
        log.info("Delete comment by a user: userId={}, commentId={}", userId, commentId);
        Comment comment = checkAndGetComment(commentId);
        if (!comment.getAuthorId().equals(userId)) {
            throw new ConflictException("Only author can delete the comment.");
        }

        commentLikeRepository.deleteAllByCommentId(commentId);
        commentRepository.deleteById(commentId);
    }

    @Override
    public void deleteComment(Long commentId) {
        log.info("Delete comment with id={}", commentId);
        checkAndGetComment(commentId);
        commentLikeRepository.deleteAllByCommentId(commentId);
        commentRepository.deleteById(commentId);
    }

    @Override
    public CommentDto addLike(Long userId, Long commentId) {
        adminClient.getUser(userId);
        Comment comment = checkAndGetComment(commentId);

        if (comment.getAuthorId().equals(userId)) {
            throw new ConflictException("You cannot like your own comment.");
        }

        if (commentLikeRepository.existsByUserIdAndCommentId(userId, commentId)) {
            throw new ConflictException("You already liked this comment");
        }

        commentLikeRepository.save(CommentLike.builder().userId(userId).comment(comment).build());

        long likesCount = commentLikeRepository.countByCommentId(commentId);

        return CommentMapper.toCommentDto(comment, CommentMapper.toShort(adminClient.getUser(comment.getAuthorId())),
                EventMapper.toEventShortDto(getEvent(comment.getEventId())), likesCount);
    }

    @Override
    public void deleteLike(Long userId, Long commentId) {
        CommentLike like = commentLikeRepository.findByUserIdAndCommentId(userId, commentId).orElseThrow(() ->
                new NotFoundException("Like not found"));

        commentLikeRepository.delete(like);
    }


    private Comment checkAndGetComment(Long commentId) {
        return commentRepository.findById(commentId).orElseThrow(() ->
                new NotFoundException("Comment with id=" + commentId + " was not found"));
    }

    private List<CommentDto> mapComments(List<Comment> comments, UserShortDto fixedAuthor, EventShortDto fixedEvent) {
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> ids = comments.stream().map(Comment::getId).toList();
        Map<Long, Long> likesMap = commentLikeRepository.countLikesForComments(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, UserShortDto> authors =
                fixedAuthor != null ? Map.of() : adminClient.getUsers(comments
                                                                      .stream()
                                                                      .map(Comment::getAuthorId)
                                                                      .distinct()
                                                                      .toList())
                                                 .stream()
                                                 .map(CommentMapper::toShort)
                                                 .collect(Collectors.toMap(UserShortDto::getId, Function.identity()));
        Map<Long, EventShortDto> events =
                fixedEvent != null ? Map.of() : eventRepository.findAllById(comments
                                                                            .stream()
                                                                            .map(Comment::getEventId)
                                                                            .distinct()
                                                                            .toList())
                                                .stream()
                                                .map(EventMapper::toEventShortDto)
                                                .collect(Collectors.toMap(EventShortDto::getId, Function.identity()));

        return comments.stream()
                .map(c ->
                        CommentMapper.toCommentDto(c, fixedAuthor != null ? fixedAuthor : authors.get(c.getAuthorId()),
                                fixedEvent != null ? fixedEvent : events.get(c.getEventId()),
                                likesMap.getOrDefault(c.getId(),
                                        0L)))
                .toList();
    }

    private EventFullDto getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .map(EventMapper::toEventFullDto).orElseThrow(() ->
                        new NotFoundException("Event with id=" + eventId + " was not found"));
    }
}