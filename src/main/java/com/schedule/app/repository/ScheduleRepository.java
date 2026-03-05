package com.schedule.app.repository;

import com.schedule.app.entity.Schedule;
import com.schedule.app.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByBranchIdAndMonthAndYear(Long branchId, Short month, Short year);
    boolean existsByBranchIdAndMonthAndYear(Long branchId, Short month, Short year);
    List<Schedule> findAllByBranchId(Long branchId);
    List<Schedule> findAllByStatus(ScheduleStatus status);

    @Query("SELECT s FROM Schedule s WHERE s.status NOT IN :excludedStatuses AND s.branch.id = :branchId")
    List<Schedule> findAllByBranchIdExcludingStatuses(
            @Param("branchId") Long branchId,
            @Param("excludedStatuses") List<ScheduleStatus> excludedStatuses
    );

    @Query("""
            SELECT s FROM Schedule s
            WHERE s.status = 'APPROVED'
            AND (s.year < :currentYear OR (s.year = :currentYear AND s.month < :currentMonth))
            """)
    List<Schedule> findAllApprovedAndExpired(
            @Param("currentMonth") Short currentMonth,
            @Param("currentYear") Short currentYear
    );
}