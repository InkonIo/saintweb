package com.schedule.app.repository;

import com.schedule.app.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findAllByBranchIdAndIsActiveTrue(Long branchId);
}
