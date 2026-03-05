package com.schedule.app.repository;

import com.schedule.app.entity.ScheduleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleVersionRepository extends JpaRepository<ScheduleVersion, Long> {
    List<ScheduleVersion> findAllByScheduleIdOrderByVersionNumberAsc(Long scheduleId);
}
