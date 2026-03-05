package com.schedule.app.service;

import com.schedule.app.entity.Schedule;
import com.schedule.app.enums.ScheduleStatus;
import com.schedule.app.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleArchiveJob {

    private final ScheduleRepository scheduleRepository;

    // Каждый день в 00:05
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void archiveExpiredSchedules() {
        LocalDate today = LocalDate.now();
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();

        List<Schedule> toArchive = scheduleRepository.findAllApprovedAndExpired(
                (short) currentMonth, (short) currentYear
        );

        for (Schedule schedule : toArchive) {
            schedule.setStatus(ScheduleStatus.ARCHIVE);
            scheduleRepository.save(schedule);
            log.info("Archived schedule id={} branch={} {}/{}",
                    schedule.getId(), schedule.getBranch().getName(),
                    schedule.getMonth(), schedule.getYear());
        }

        if (!toArchive.isEmpty()) {
            log.info("Auto-archived {} schedules", toArchive.size());
        }
    }
}