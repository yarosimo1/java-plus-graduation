package ru.practicum.ewm.categories.controller.internal;

import ru.practicum.ewm.categories.mapper.CategoryMapper;
import ru.practicum.ewm.categories.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.categories.dto.CategoryDto;
import ru.practicum.ewm.error.NotFoundException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/categories")
public class CategoryInternalController {
    private final CategoryRepository categoryRepository;

    @GetMapping("/{categoryId}")
    public CategoryDto getCategory(@PathVariable Long categoryId) {
        return categoryRepository.findById(categoryId)
                .map(CategoryMapper::toCategoryDto)
                .orElseThrow(() -> new NotFoundException("Category with id=" + categoryId + " was not found"));
    }

    @GetMapping
    public List<CategoryDto> getCategories(@RequestParam List<Long> ids) {
        return categoryRepository.findAllById(ids).stream()
                .map(CategoryMapper::toCategoryDto)
                .toList();
    }
}
