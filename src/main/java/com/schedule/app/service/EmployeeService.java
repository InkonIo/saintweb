package com.schedule.app.service;

import com.schedule.app.dto.request.EmployeeRequest;
import com.schedule.app.dto.response.EmployeeResponse;
import com.schedule.app.entity.Branch;
import com.schedule.app.entity.Employee;
import com.schedule.app.entity.User;
import com.schedule.app.exception.ResourceNotFoundException;
import com.schedule.app.repository.EmployeeRepository;
import com.schedule.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final BranchService branchService;
    private final UserRepository userRepository;

    public List<EmployeeResponse> getAllByBranch(Long branchId) {
        branchService.findById(branchId);
        return employeeRepository.findAllByBranchIdAndIsActiveTrue(branchId)
                .stream().map(this::toResponse).toList();
    }

    public EmployeeResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest.Create request) {
        Branch branch = branchService.findById(request.branchId());

        User user = null;
        if (request.userId() != null) {
            user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.userId()));
        }

        Employee employee = Employee.builder()
                .branch(branch)
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .position(request.position())
                .build();

        return toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest.Update request) {
        Employee employee = findById(id);

        if (request.userId() != null) {
            User user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.userId()));
            employee.setUser(user);
        }
        if (request.firstName() != null) employee.setFirstName(request.firstName());
        if (request.lastName() != null) employee.setLastName(request.lastName());
        if (request.position() != null) employee.setPosition(request.position());
        if (request.isActive() != null) employee.setIsActive(request.isActive());

        return toResponse(employeeRepository.save(employee));
    }

    public Employee findById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }

    private EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(
                e.getId(),
                e.getBranch().getId(),
                e.getBranch().getName(),
                e.getUser() != null ? e.getUser().getId() : null,
                e.getFirstName(),
                e.getLastName(),
                e.getPosition(),
                e.getIsActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
