package com.schedule.app.repository;

import com.schedule.app.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findAllByBranchIdAndIsActiveTrue(Long branchId);
    Optional<Employee> findByUserId(Long userId);
}
