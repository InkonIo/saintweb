package com.schedule.app.ai.repository;

import com.schedule.app.ai.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    // ── FIX #2: история по userId + scheduleId ──
    List<AiConversation> findTop20ByUserIdAndScheduleIdOrderByCreatedAtAsc(Long userId, Long scheduleId);

    // история без привязки к графику (общий чат)
    List<AiConversation> findTop20ByUserIdAndScheduleIdIsNullOrderByCreatedAtAsc(Long userId);

    // старый метод — можно оставить для совместимости или удалить
    List<AiConversation> findTop20ByUserIdOrderByCreatedAtAsc(Long userId);

    // ── FIX #4: удаление по userId + scheduleId ──
    void deleteAllByUserIdAndScheduleId(Long userId, Long scheduleId);

    void deleteAllByUserId(Long userId);
}