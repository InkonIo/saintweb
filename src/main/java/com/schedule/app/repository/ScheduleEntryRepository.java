package com.schedule.app.repository;

import com.schedule.app.entity.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {
    List<ScheduleEntry> findAllByScheduleId(Long scheduleId);
    void deleteAllByScheduleId(Long scheduleId);
}
