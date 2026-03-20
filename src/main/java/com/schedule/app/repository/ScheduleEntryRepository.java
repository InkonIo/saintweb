package com.schedule.app.repository;

import com.schedule.app.entity.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {
    List<ScheduleEntry> findAllByScheduleId(Long scheduleId);
    void deleteAllByScheduleId(Long scheduleId);

    // Все записи сотрудника по утверждённым графикам
    @Query(value = """
    SELECT se.* FROM schedule_entries se
    JOIN schedules s ON s.id = se.schedule_id
    WHERE se.employee_id = :employeeId
    AND s.status = 'APPROVED'
    ORDER BY se.work_date DESC
    """, nativeQuery = true)
List<ScheduleEntry> findApprovedEntriesByEmployeeId(@Param("employeeId") Long employeeId);

@Query(value = """
    SELECT se.shift_type, COUNT(se.id)
    FROM schedule_entries se
    JOIN schedules s ON s.id = se.schedule_id
    WHERE se.employee_id = :employeeId
    AND s.status = 'APPROVED'
    GROUP BY se.shift_type
    """, nativeQuery = true)
List<Object[]> getAnalyticsByEmployee(@Param("employeeId") Long employeeId);

@Query(value = """
    SELECT se.shift_type FROM schedule_entries se
    WHERE se.employee_id = :employeeId
    AND se.work_date = :workDate
    AND se.schedule_id != :scheduleId
    """, nativeQuery = true)
List<Object[]> findOtherScheduleEntriesForEmployee(
        @Param("employeeId") Long employeeId,
        @Param("workDate") java.time.LocalDate workDate,
        @Param("scheduleId") Long scheduleId);
}