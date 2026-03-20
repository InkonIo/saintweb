package com.schedule.app.service;

import com.schedule.app.entity.Schedule;
import com.schedule.app.entity.ScheduleEntry;
import com.schedule.app.repository.ScheduleEntryRepository;
import com.schedule.app.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConflictService {

    private final ScheduleEntryRepository entryRepository;
    private final ScheduleRepository scheduleRepository;

    public List<Map<String, Object>> checkConflicts(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        List<ScheduleEntry> entries = entryRepository.findAllByScheduleId(scheduleId);
        List<Map<String, Object>> conflicts = new ArrayList<>();

        // Группируем по сотруднику
        Map<Long, List<ScheduleEntry>> byEmployee = new HashMap<>();
        for (ScheduleEntry e : entries) {
            byEmployee.computeIfAbsent(e.getEmployee().getId(), k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<Long, List<ScheduleEntry>> emp : byEmployee.entrySet()) {
            Long employeeId = emp.getKey();
            List<ScheduleEntry> empEntries = emp.getValue();
            empEntries.sort(Comparator.comparing(ScheduleEntry::getWorkDate));

            // 1. Проверка: отпуск/больничный + рабочая смена в один день
            for (ScheduleEntry e : empEntries) {
                String shift = e.getShiftType().toUpperCase();
                if ((shift.equals("О") || shift.equals("Б")) ) {
                    // проверяем нет ли рабочей смены в другом графике в эту дату
                    List<Object[]> others = entryRepository.findOtherScheduleEntriesForEmployee(
                            employeeId, e.getWorkDate(), scheduleId);
                    for (Object[] other : others) {
                        String otherShift = (String) other[0];
                        if (!otherShift.equals("В") && !otherShift.equals("О") && !otherShift.equals("Б")) {
                            Map<String, Object> conflict = new HashMap<>();
                            conflict.put("employeeId", employeeId);
                            conflict.put("workDate", e.getWorkDate().toString());
                            conflict.put("type", "OVERLAP");
                            conflict.put("message", "Сотрудник уже работает в другом графике в этот день");
                            conflicts.add(conflict);
                        }
                    }
                }
            }

            // 2. Проверка: больше 6 рабочих дней подряд
            int streak = 0;
            LocalDate streakStart = null;
            for (ScheduleEntry e : empEntries) {
                String shift = e.getShiftType().toUpperCase();
                boolean isWork = !shift.equals("В") && !shift.equals("О") &&
                                 !shift.equals("Б") && !shift.equals("БС") && !shift.isEmpty();
                if (isWork) {
                    streak++;
                    if (streakStart == null) streakStart = e.getWorkDate();
                    if (streak > 6) {
                        Map<String, Object> conflict = new HashMap<>();
                        conflict.put("employeeId", employeeId);
                        conflict.put("workDate", e.getWorkDate().toString());
                        conflict.put("type", "TOO_MANY_DAYS");
                        conflict.put("message", "Более 6 рабочих дней подряд без выходного");
                        conflicts.add(conflict);
                    }
                } else {
                    streak = 0;
                    streakStart = null;
                }
            }
        }

        return conflicts;
    }
}