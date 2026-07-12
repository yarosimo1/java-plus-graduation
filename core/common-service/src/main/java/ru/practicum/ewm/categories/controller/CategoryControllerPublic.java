package ru.practicum.ewm.categories.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.categories.service.CategoryService;
import ru.practicum.ewm.categories.dto.CategoryDto;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ru/practicum/ewm/categories")
@Validated
public class CategoryControllerPublic {
    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryDto> getAll(@RequestParam(value = "from", defaultValue = "0") @PositiveOrZero Integer from,
                                    @RequestParam(value = "size", defaultValue = "10") @Positive Integer size) {
        return categoryService.getAll(from, size);
    }

    @GetMapping("/{categoryId}")
    public CategoryDto getById(@PathVariable Long categoryId) {
        return categoryService.getById(categoryId);
    }
}
