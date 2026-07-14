package ru.practicum.ewm.categories.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.categories.mapper.CategoryMapper;
import ru.practicum.ewm.categories.model.Category;
import ru.practicum.ewm.categories.repository.CategoryRepository;
import ru.practicum.ewm.categories.dto.CategoryDto;
import ru.practicum.ewm.categories.dto.NewCategoryDto;
import ru.practicum.ewm.error.ConflictException;
import ru.practicum.ewm.error.NotFoundException;
import ru.practicum.ewm.events.repository.EventRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public CategoryDto createCategory(NewCategoryDto newCategoryDto) {
        log.info("Создаем категорию: name={}", newCategoryDto.getName());
        if (categoryRepository.existsByName(newCategoryDto.getName())) {
            throw new ConflictException(
                    String.format("Категория с наименованием: \"%s\" уже существует", newCategoryDto.getName()));
        }
        Category saved = categoryRepository.save(CategoryMapper.toCategory(newCategoryDto));
        return CategoryMapper.toCategoryDto(saved);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long id, CategoryDto categoryDto) {
        log.info("Обновляем категорию: name={}", categoryDto.getName());
        Category category = getCategory(id);
        if (!category.getName().equals(categoryDto.getName()) && categoryRepository.existsByName(categoryDto.getName())) {
            throw new ConflictException(
                    String.format("Категория с наименованием: \"%s\" уже существует", categoryDto.getName()));
        }
        category.setName(categoryDto.getName());

        Category updatedCategory = categoryRepository.save(category);
        return CategoryMapper.toCategoryDto(updatedCategory);
    }

    @Override
    public List<CategoryDto> getAll(Integer from, Integer size) {
        log.info("Получаем категории: from={}, size={}", from, size);
        Map<Long, CategoryDto> categories = new LinkedHashMap<>();
        categoryRepository.findAll().stream()
                .map(CategoryMapper::toCategoryDto)
                .forEach(category -> categories.put(category.getId(), category));

        eventRepository.findDistinctCategorySnapshots().forEach(snapshot -> {
            Long id = (Long) snapshot[0];
            String name = (String) snapshot[1];
            categories.putIfAbsent(id, new CategoryDto(id, name));
        });

        return categories.values().stream()
                .sorted(Comparator.comparing(CategoryDto::getId).reversed())
                .skip(from)
                .limit(size)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCategoryById(Long id) {
        log.info("Удаляем категорию с id={}", id);
        if (!categoryRepository.existsById(id)) {
            throw new NotFoundException(String.format("Категория с id: %s не найдена", id));
        }
        if (eventRepository.existsByCategoryId(id)) {
            throw new ConflictException(String.format("Категория с id: %s используется в событиях", id));
        }
        categoryRepository.deleteById(id);
    }

    @Override
    public CategoryDto getById(Long id) {
        log.info("Получаем категорию с id={}", id);
        Category findCategory = getCategory(id);
        return CategoryMapper.toCategoryDto(findCategory);
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Категория с id: %s не найдена", id)));
    }
}
