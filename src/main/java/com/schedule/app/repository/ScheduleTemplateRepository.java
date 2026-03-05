package com.schedule.app.repository;

import com.schedule.app.entity.ScheduleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long> {
}
