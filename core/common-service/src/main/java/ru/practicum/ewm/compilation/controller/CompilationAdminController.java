package ru.practicum.ewm.compilation.controller;

import ru.practicum.ewm.compilation.service.CompilationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;

@RestController
@RequestMapping("/admin/compilations")
@RequiredArgsConstructor
public class CompilationAdminController {

    private final CompilationService compilationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompilationDto saveCompilation(@RequestBody @Valid NewCompilationDto dto) {
        return compilationService.saveCompilation(dto);
    }

    @DeleteMapping("/{compId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompilation(@PathVariable long compId) {
        compilationService.deleteCompilation(compId);
    }

    @PatchMapping("/{compId}")
    public CompilationDto updateCompilation(@PathVariable long compId,
            @RequestBody @Valid UpdateCompilationRequest request) {
        return compilationService.updateCompilation(compId, request);
    }
}
