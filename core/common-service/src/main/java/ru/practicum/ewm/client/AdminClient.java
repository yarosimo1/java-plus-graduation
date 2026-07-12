package ru.practicum.ewm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.ewm.user.dto.UserDto;

import java.util.List;

@FeignClient(name = "admin-service", path = "/internal/users")
public interface AdminClient {
    @GetMapping("/{userId}")
    UserDto getUser(@PathVariable Long userId);

    @GetMapping
    List<UserDto> getUsers(@RequestParam List<Long> ids);
}
