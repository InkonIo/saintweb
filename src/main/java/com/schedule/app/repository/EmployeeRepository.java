package com.schedule.app.repository;

import com.schedule.app.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findAllByBranchIdAndIsActiveTrue(Long branchId);
    Optional<Employee> findByUserId(Long userId);

    @Query("SELECT e FROM Employee e JOIN FETCH e.branch WHERE e.branch.id = :branchId AND e.isActive = true")
    List<Employee> findAllByBranchIdAndIsActiveTrueWithBranch(@Param("branchId") Long branchId);
}
