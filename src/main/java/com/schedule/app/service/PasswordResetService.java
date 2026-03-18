package com.schedule.app.service;

import com.schedule.app.entity.PasswordResetToken;
import com.schedule.app.entity.User;
import com.schedule.app.exception.BusinessException;
import com.schedule.app.repository.PasswordResetTokenRepository;
import com.schedule.app.repository.UserRepository;
import com.schedule.app.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public void sendResetCode(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Пользователь с таким email не найден"));

        // Удаляем старые токены
        tokenRepository.deleteAllByUserId(user.getId());

        // Генерируем 5-значный код
        String code = String.format("%05d", new Random().nextInt(100000));

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .token(code)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();

        tokenRepository.save(resetToken);

        // Отправляем письмо
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Восстановление пароля");
        message.setText("Ваш код для восстановления пароля: " + code + "\n\nКод действителен 10 минут.");
        mailSender.send(message);
    }

    @Transactional
    public String verifyCode(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Пользователь не найден"));

        PasswordResetToken resetToken = tokenRepository.findByTokenAndUsedFalse(code)
                .orElseThrow(() -> new BusinessException("Неверный или истёкший код"));

        if (!resetToken.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Неверный код");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Код истёк, запросите новый");
        }

        // Помечаем код как использованный
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Возвращаем JWT как reset-токен
        return jwtService.generateToken(user);
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        String username = jwtService.extractUsername(resetToken);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Пользователь не найден"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}