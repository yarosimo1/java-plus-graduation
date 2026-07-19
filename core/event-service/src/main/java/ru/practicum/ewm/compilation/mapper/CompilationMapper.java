package ru.practicum.ewm.compilation.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.events.dto.EventShortDto;


import java.util.List;
import java.util.Set;

@UtilityClass
public class CompilationMapper {

    public CompilationDto toCompilationDto(Compilation compilation, List<EventShortDto> events) {
        return CompilationDto.builder()
                .id(compilation.getId())
                .pinned(compilation.getPinned())
                .events(events)
                .title(compilation.getTitle())
                .build();
    }

    public static Compilation toCompilation(CompilationDto compilationDto, Set<Long> eventIds) {
        Compilation compilation = new Compilation();
        compilation.setId(compilationDto.getId());
        compilation.setEventIds(eventIds);
        compilation.setPinned(compilationDto.getPinned() != null ? compilationDto.getPinned() : false);
        compilation.setTitle(compilationDto.getTitle());

        return compilation;
    }
}
