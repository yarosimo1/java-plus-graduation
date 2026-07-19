package ru.practicum.request.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.ewm.user.dto.UserDto;

@FeignClient(name = "user-service", path = "/internal/users")
public interface AdminClient {
    @GetMapping("/{userId}")
    UserDto getUser(@PathVariable Long userId);
}
