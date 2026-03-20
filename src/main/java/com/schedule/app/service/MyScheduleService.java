package com.schedule.app.service;

import com.schedule.app.entity.Employee;
import com.schedule.app.entity.Schedule;
import com.schedule.app.entity.ScheduleEntry;
import com.schedule.app.entity.User;
import com.schedule.app.exception.BusinessException;
import com.schedule.app.repository.EmployeeRepository;
import com.schedule.app.repository.ScheduleEntryRepository;
import com.schedule.app.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MyScheduleService {

    private final EmployeeRepository employeeRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final ScheduleRepository scheduleRepository;

    public Employee getMyEmployee(User user) {
        return employeeRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("Профиль сотрудника не найден"));
    }

    public List<Map<String, Object>> getMySchedules(User user) {
    Employee employee = getMyEmployee(user);
    List<ScheduleEntry> entries = scheduleEntryRepository.findApprovedEntriesByEmployeeId(employee.getId());

    return entries.stream().map(e -> {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", e.getId());
        map.put("workDate", e.getWorkDate().toString());
        map.put("shiftType", e.getShiftType());
        map.put("scheduleId", e.getSchedule().getId());
        return map;
    }).collect(java.util.stream.Collectors.toList());
}

    public Map<String, Object> getMyAnalytics(User user) {
        Employee employee = getMyEmployee(user);
        List<Object[]> raw = scheduleEntryRepository.getAnalyticsByEmployee(employee.getId());

        long totalDays = 0, workDays = 0, vacationDays = 0, sickDays = 0, dayOff = 0;

        for (Object[] row : raw) {
            String shiftType = (String) row[0];
            long count = ((Number) row[1]).longValue();
            totalDays += count;
            switch (shiftType.toUpperCase()) {
                case "В" -> dayOff += count;
                case "О" -> vacationDays += count;
                case "Б" -> sickDays += count;
                default -> workDays += count;
            }
        }

        return Map.of(
                "employeeId", employee.getId(),
                "fullName", employee.getFirstName() + " " + employee.getLastName(),
                "totalDays", totalDays,
                "workDays", workDays,
                "dayOff", dayOff,
                "vacationDays", vacationDays,
                "sickDays", sickDays
        );
    }
}