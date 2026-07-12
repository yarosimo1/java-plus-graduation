package ru.practicum.ewm.user.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.user.dto.UserDto;
import ru.practicum.ewm.user.mapper.UserMapper;
import ru.practicum.ewm.user.repository.UserRepository;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class UserInternalController {
    private final UserRepository userRepository;

    @GetMapping("/{userId}")
    public UserDto getUser(@PathVariable Long userId) {
        return userRepository.findById(userId)
                .map(UserMapper::toUserDto)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    @GetMapping
    public List<UserDto> getUsers(@RequestParam List<Long> ids) {
        return userRepository.findByIds(ids, org.springframework.data.domain.Pageable.unpaged()).stream()
                .map(UserMapper::toUserDto)
                .toList();
    }
}
