package ru.practicum.events.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.ewm.categories.dto.CategoryDto;

import java.util.List;

@FeignClient(name = "common-service", path = "/internal/categories")
public interface CommonClient {
    @GetMapping("/{categoryId}")
    CategoryDto getCategory(@PathVariable Long categoryId);

    @GetMapping
    List<CategoryDto> getCategories(@RequestParam List<Long> ids);
}
