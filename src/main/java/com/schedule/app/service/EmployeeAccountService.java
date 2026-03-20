package com.schedule.app.service;

import com.schedule.app.dto.request.CreateEmployeeAccountRequest;
import com.schedule.app.dto.response.AuthResponse;
import com.schedule.app.entity.Employee;
import com.schedule.app.entity.User;
import com.schedule.app.enums.UserRole;
import com.schedule.app.exception.BusinessException;
import com.schedule.app.repository.EmployeeRepository;
import com.schedule.app.repository.UserRepository;
import com.schedule.app.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeAccountService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse.TokenResponse createAccount(Long employeeId, CreateEmployeeAccountRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("Сотрудник не найден"));

        if (employee.getUser() != null) {
            throw new BusinessException("У сотрудника уже есть аккаунт");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("Логин уже занят");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email уже используется");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.EMPLOYEE)
                .build();

        userRepository.save(user);

        employee.setUser(user);
        employeeRepository.save(employee);

        String token = jwtService.generateToken(user);
        return new AuthResponse.TokenResponse(token, user.getUsername(), user.getEmail(), user.getRole());
    }
}