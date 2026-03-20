package com.schedule.app.service;

import com.schedule.app.entity.Schedule;
import com.schedule.app.entity.ScheduleEntry;
import com.schedule.app.enums.ScheduleStatus;
import com.schedule.app.repository.ScheduleEntryRepository;
import com.schedule.app.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleEntryRepository entryRepository;

    public Map<String, Object> getSummary() {
        List<Schedule> all = scheduleRepository.findAll();

        long total = all.size();
        long draft = all.stream().filter(s -> s.getStatus() == ScheduleStatus.DRAFT).count();
        long pending = all.stream().filter(s -> s.getStatus() == ScheduleStatus.PENDING).count();
        long approved = all.stream().filter(s -> s.getStatus() == ScheduleStatus.APPROVED).count();
        long revision = all.stream().filter(s -> s.getStatus() == ScheduleStatus.REVISION).count();
        long archived = all.stream().filter(s -> s.getStatus() == ScheduleStatus.ARCHIVE).count();

        // Статистика по месяцам (последние 6)
        Map<String, Long> byMonth = new LinkedHashMap<>();
        all.stream()
            .filter(s -> s.getStatus() == ScheduleStatus.APPROVED)
            .sorted(Comparator.comparingInt((Schedule s) -> s.getYear() * 100 + s.getMonth()))
            .forEach(s -> {
                String key = s.getMonth() + "/" + s.getYear();
                byMonth.merge(key, 1L, Long::sum);
            });

        // Топ филиалов по количеству графиков
        Map<String, Long> byBranch = new LinkedHashMap<>();
        all.forEach(s -> byBranch.merge(s.getBranch().getName(), 1L, Long::sum));

        // Общее число записей по типам смен
        List<ScheduleEntry> entries = entryRepository.findAll();
        Map<String, Long> shiftStats = new LinkedHashMap<>();
        entries.forEach(e -> shiftStats.merge(e.getShiftType(), 1L, Long::sum));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("draft", draft);
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("revision", revision);
        result.put("archived", archived);
        result.put("byMonth", byMonth);
        result.put("byBranch", byBranch);
        result.put("shiftStats", shiftStats);

        return result;
    }
}