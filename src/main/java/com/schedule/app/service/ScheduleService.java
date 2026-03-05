package com.schedule.app.service;

import com.schedule.app.dto.request.ScheduleRequest;
import com.schedule.app.dto.response.ScheduleResponse;
import com.schedule.app.entity.*;
import com.schedule.app.enums.AuditAction;
import com.schedule.app.enums.NotificationType;
import com.schedule.app.enums.ScheduleStatus;
import com.schedule.app.enums.ShiftType;
import com.schedule.app.exception.BusinessException;
import com.schedule.app.exception.ResourceNotFoundException;
import com.schedule.app.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleEntryRepository entryRepository;
    private final ScheduleVersionRepository versionRepository;
    private final ScheduleTemplateRepository templateRepository;
    private final BranchService branchService;
    private final EmployeeService employeeService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public ScheduleResponse.Full create(ScheduleRequest.Create request) {
        User author = getCurrentUser();
        Branch branch = branchService.findById(request.branchId());

        Short month = request.month().shortValue();
        Short year = request.year().shortValue();

        Optional<Schedule> existing = scheduleRepository.findByBranchIdAndMonthAndYear(branch.getId(), month, year);
        if (existing.isPresent() && existing.get().getStatus() != ScheduleStatus.ARCHIVE) {
            throw new BusinessException(
                    "Active schedule already exists for this branch/month/year. ID: " + existing.get().getId()
            );
        }

        ScheduleTemplate template = null;
        if (request.templateId() != null) {
            template = templateRepository.findById(request.templateId())
                    .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + request.templateId()));
        }

        Schedule schedule = Schedule.builder()
                .branch(branch)
                .template(template)
                .author(author)
                .month(month)
                .year(year)
                .build();

        scheduleRepository.save(schedule);
        saveVersion(schedule, author, null);

        auditService.log(author, AuditAction.SCHEDULE_CREATED, "Schedule", schedule.getId(),
                "Branch: " + branch.getName() + " " + month + "/" + year, null);

        return toFull(schedule);
    }

    public List<ScheduleResponse.Short> getAll() {
        return scheduleRepository.findAll().stream().map(this::toShort).toList();
    }

    public List<ScheduleResponse.Short> getAllByBranch(Long branchId) {
        return scheduleRepository.findAllByBranchId(branchId).stream().map(this::toShort).toList();
    }

    public ScheduleResponse.Full getById(Long id) {
        return toFull(findById(id));
    }

    public List<ScheduleResponse.VersionResponse> getVersionHistory(Long scheduleId) {
        findById(scheduleId);
        return versionRepository.findAllByScheduleIdOrderByVersionNumberAsc(scheduleId)
                .stream().map(this::toVersionResponse).toList();
    }

    @Transactional
    public ScheduleResponse.Full updateEntries(Long scheduleId, ScheduleRequest.UpdateEntries request) {
        Schedule schedule = findById(scheduleId);
        validateEditable(schedule);

        // Валидация shift_type
        for (ScheduleRequest.UpdateEntries.EntryItem item : request.entries()) {
            try {
                ShiftType.fromCode(item.shiftType());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid shift type: '" + item.shiftType() +
                        "'. Allowed: 9-18, 9-21, 8-17, 8-20, В, О, Б, БС, К, Д");
            }
        }

        // Правильный способ: clear() + addAll() — Hibernate сам удалит через orphanRemoval
        schedule.getEntries().clear();
        scheduleRepository.saveAndFlush(schedule); // flush чтобы DELETE прошёл до INSERT

        List<ScheduleEntry> entries = request.entries().stream().map(item -> {
            Employee employee = employeeService.findById(item.employeeId());
            return ScheduleEntry.builder()
                    .schedule(schedule)
                    .employee(employee)
                    .workDate(item.workDate())
                    .shiftType(item.shiftType())
                    .build();
        }).toList();

        schedule.getEntries().addAll(entries);
        scheduleRepository.save(schedule);

        User currentUser = getCurrentUser();
        auditService.log(currentUser, AuditAction.SCHEDULE_UPDATED, "Schedule", schedule.getId(),
                "Entries updated: " + entries.size() + " records", null);

        return toFull(schedule);
    }

    @Transactional
    public ScheduleResponse.Short submitForApproval(Long scheduleId) {
        Schedule schedule = findById(scheduleId);
        validateEditable(schedule);

        schedule.setStatus(ScheduleStatus.PENDING);
        scheduleRepository.save(schedule);

        User currentUser = getCurrentUser();
        saveVersion(schedule, currentUser, null);

        notificationService.notifyReviewers(schedule, NotificationType.SCHEDULE_SUBMITTED,
                "График " + schedule.getBranch().getName() + " за " +
                        schedule.getMonth() + "/" + schedule.getYear() + " ожидает согласования");

        auditService.log(currentUser, AuditAction.SCHEDULE_SUBMITTED, "Schedule", schedule.getId(),
                "Submitted for approval", null);

        return toShort(schedule);
    }

    @Transactional
    public ScheduleResponse.Short approve(Long scheduleId) {
        Schedule schedule = findById(scheduleId);
        if (schedule.getStatus() != ScheduleStatus.PENDING) {
            throw new BusinessException("Schedule must be PENDING to approve. Current: " + schedule.getStatus());
        }

        schedule.setStatus(ScheduleStatus.APPROVED);
        scheduleRepository.save(schedule);

        User currentUser = getCurrentUser();
        saveVersion(schedule, currentUser, null);

        notificationService.notifyUser(schedule.getAuthor(), schedule, NotificationType.SCHEDULE_APPROVED,
                "График " + schedule.getBranch().getName() + " за " +
                        schedule.getMonth() + "/" + schedule.getYear() + " утверждён");

        auditService.log(currentUser, AuditAction.SCHEDULE_APPROVED, "Schedule", schedule.getId(),
                "Approved by " + currentUser.getUsername(), null);

        return toShort(schedule);
    }

    @Transactional
    public ScheduleResponse.Short sendToRevision(Long scheduleId, ScheduleRequest.Review request) {
        Schedule schedule = findById(scheduleId);
        if (schedule.getStatus() != ScheduleStatus.PENDING) {
            throw new BusinessException("Schedule must be PENDING to send for revision. Current: " + schedule.getStatus());
        }

        schedule.setStatus(ScheduleStatus.REVISION);
        schedule.setVersion((short) (schedule.getVersion() + 1));
        scheduleRepository.save(schedule);

        User currentUser = getCurrentUser();
        saveVersion(schedule, currentUser, request.comment());

        notificationService.notifyUser(schedule.getAuthor(), schedule, NotificationType.SCHEDULE_REVISION,
                "График " + schedule.getBranch().getName() + " за " +
                        schedule.getMonth() + "/" + schedule.getYear() +
                        " возвращён на доработку. Комментарий: " + request.comment());

        auditService.log(currentUser, AuditAction.SCHEDULE_REVISION, "Schedule", schedule.getId(),
                "Sent to revision. Comment: " + request.comment(), null);

        return toShort(schedule);
    }

    @Transactional
    public ScheduleResponse.Short archiveSchedule(Long scheduleId) {
        Schedule schedule = findById(scheduleId);
        schedule.setStatus(ScheduleStatus.ARCHIVE);
        scheduleRepository.save(schedule);

        auditService.log(getCurrentUser(), AuditAction.SCHEDULE_ARCHIVED, "Schedule", schedule.getId(),
                "Manually archived", null);

        return toShort(schedule);
    }

    private void validateEditable(Schedule schedule) {
        if (schedule.getStatus() == ScheduleStatus.PENDING || schedule.getStatus() == ScheduleStatus.APPROVED) {
            throw new BusinessException("Cannot edit schedule in status: " + schedule.getStatus());
        }
    }

    private void saveVersion(Schedule schedule, User changedBy, String comment) {
        ScheduleVersion version = ScheduleVersion.builder()
                .schedule(schedule)
                .versionNumber(schedule.getVersion())
                .changedBy(changedBy)
                .status(schedule.getStatus())
                .comment(comment)
                .build();
        versionRepository.save(version);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Current user not found"));
    }

    public Schedule findById(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + id));
    }

    private ScheduleResponse.Short toShort(Schedule s) {
        return new ScheduleResponse.Short(
                s.getId(), s.getBranch().getId(), s.getBranch().getName(),
                s.getMonth().intValue(), s.getYear().intValue(),
                s.getStatus(), s.getVersion().intValue(),
                s.getAuthor().getUsername(), s.getCreatedAt(), s.getUpdatedAt()
        );
    }

    private ScheduleResponse.Full toFull(Schedule s) {
        List<ScheduleResponse.EntryResponse> entries = entryRepository.findAllByScheduleId(s.getId())
                .stream().map(e -> new ScheduleResponse.EntryResponse(
                        e.getId(), e.getEmployee().getId(),
                        e.getEmployee().getFirstName(), e.getEmployee().getLastName(),
                        e.getWorkDate(), e.getShiftType()
                )).toList();

        return new ScheduleResponse.Full(
                s.getId(), s.getBranch().getId(), s.getBranch().getName(),
                s.getTemplate() != null ? s.getTemplate().getId() : null,
                s.getTemplate() != null ? s.getTemplate().getName() : null,
                s.getMonth().intValue(), s.getYear().intValue(),
                s.getStatus(), s.getVersion().intValue(),
                s.getAuthor().getUsername(), entries, s.getCreatedAt(), s.getUpdatedAt()
        );
    }

    private ScheduleResponse.VersionResponse toVersionResponse(ScheduleVersion v) {
        return new ScheduleResponse.VersionResponse(
                v.getId(), v.getVersionNumber().intValue(),
                v.getChangedBy().getUsername(), v.getChangedAt(),
                v.getStatus(), v.getComment()
        );
    }
}