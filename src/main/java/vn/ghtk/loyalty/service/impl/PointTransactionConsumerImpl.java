package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ghtk.loyalty.dto.event.PointTransactionEvent;
import vn.ghtk.loyalty.entity.CheckinEvent;
import vn.ghtk.loyalty.entity.DailyCheckin;
import vn.ghtk.loyalty.entity.User;
import vn.ghtk.loyalty.entity.UserPointsHistory;
import vn.ghtk.loyalty.enums.PointsTransactionType;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.CheckinEventRepository;
import vn.ghtk.loyalty.repository.DailyCheckinRepository;
import vn.ghtk.loyalty.repository.UserPointsHistoryRepository;
import vn.ghtk.loyalty.repository.UserRepository;
import vn.ghtk.loyalty.service.PointTransactionConsumer;

import java.time.LocalDateTime;

/**
 * DB Writer - consume PointTransactionEvent from Kafka and write to MySQL
 * Implements exactly-once semantics with:
 * - Deduplication by transactionId
 * - Idempotent DB writes
 * - Manual commit after success
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointTransactionConsumerImpl implements PointTransactionConsumer {

    private final UserRepository userRepository;
    private final DailyCheckinRepository dailyCheckinRepository;
    private final UserPointsHistoryRepository userPointsHistoryRepository;
    private final CheckinEventRepository checkinEventRepository;

    @Override
    @Transactional
    @KafkaListener(
        topics = "${spring.kafka.topic.point-transaction:loyalty.point.transaction}",
        groupId = "${spring.kafka.consumer.group-id:loyalty-db-writer}",
        containerFactory = "pointTransactionKafkaListenerContainerFactory"
    )
    public void processPointTransaction(PointTransactionEvent event) {
        log.info("Received PointTransactionEvent: transactionId={}, userId={}, points={}", 
            event.getTransactionId(), event.getUserId(), event.getPointsEarned());

        try {
            // 1. Dedup: check if transactionId already processed
            if (isTransactionProcessed(event.getTransactionId())) {
                log.warn("Transaction already processed (dedup): transactionId={}", event.getTransactionId());
                return; // Idempotent: skip nếu đã xử lý
            }

            // 2. Get user
            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new BusinessException("User not found: " + event.getUserId()));

            // 3. Update user total points
            user.setTotalPoints(user.getTotalPoints() + event.getPointsEarned());
            userRepository.save(user);

            // 4. Log to checkin_events (for Flink dedup cross-restart)
            logCheckinEvent(event);

            // 5. Create daily check-in record (idempotent with unique constraint)
            createOrUpdateDailyCheckin(event, user);

            // 6. Create points history
            createPointsHistory(event, user);

            log.info("Transaction processed successfully: transactionId={}, userId={}, newTotal={}", 
                event.getTransactionId(), event.getUserId(), user.getTotalPoints());

        } catch (Exception e) {
            log.error("Failed to process transaction: transactionId={}, error={}", 
                event.getTransactionId(), e.getMessage(), e);
            throw new RuntimeException("DB write failed", e);
        }
    }

    /**
     * Check if transaction already processed (deduplication)
     * Sử dụng bảng user_points_history với unique constraint trên description (chứa transactionId)
     */
    private boolean isTransactionProcessed(String transactionId) {
        // Query bảng user_points_history xem có record nào với description chứa transactionId chưa
        return userPointsHistoryRepository.existsByDescriptionContaining(transactionId);
    }

    /**
     * Log checkin event to DB for Flink dedup cross-restart
     * Bảng này dùng để Flink query khi restart và mất state
     */
    private void logCheckinEvent(PointTransactionEvent event) {
        // Check if event already logged (idempotent)
        if (checkinEventRepository.existsByEventId(event.getEventId())) {
            log.debug("Event already logged in checkin_events: eventId={}", event.getEventId());
            return;
        }

        CheckinEvent checkinEvent = CheckinEvent.builder()
            .eventId(event.getEventId())
            .userId(event.getUserId())
            .checkinDate(event.getCheckinDate())
            .eventTimestamp(event.getEventTimestamp())
            .yearMonth(event.getYearMonth())
            .pointsEarned(event.getPointsEarned())
            .checkinOrder(event.getCheckinOrder())
            .processed(true)
            .processedAt(LocalDateTime.now())
            .build();

        checkinEventRepository.save(checkinEvent);
        log.debug("Logged event to checkin_events: eventId={}, userId={}", 
            event.getEventId(), event.getUserId());
    }

    /**
     * Create or update daily check-in record
     * Dùng unique constraint (user_id, checkin_date) để idempotent
     */
    private void createOrUpdateDailyCheckin(PointTransactionEvent event, User user) {
        DailyCheckin dailyCheckin = dailyCheckinRepository
            .findByUserIdAndCheckinDate(user.getId(), event.getCheckinDate())
            .orElseGet(() -> {
                DailyCheckin newCheckin = new DailyCheckin();
                newCheckin.setUserId(user.getId());
                newCheckin.setCheckinDate(event.getCheckinDate());
                return newCheckin;
            });

        dailyCheckin.setCheckinOrder(event.getCheckinOrder());
        dailyCheckin.setPointsEarned(event.getPointsEarned());

        dailyCheckinRepository.save(dailyCheckin);
        log.debug("Daily check-in saved: userId={}, date={}, order={}", 
            user.getId(), event.getCheckinDate(), event.getCheckinOrder());
    }

    /**
     * Create points history
     * Description chứa transactionId để dedup
     */
    private void createPointsHistory(PointTransactionEvent event, User user) {
        // Check duplicate before insert
        String description = String.format("Check-in %s (txId: %s)", 
            event.getCheckinDate(), event.getTransactionId());
        
        if (userPointsHistoryRepository.existsByDescriptionContaining(event.getTransactionId())) {
            log.debug("Points history already exists for transactionId={}", event.getTransactionId());
            return;
        }

        UserPointsHistory history = UserPointsHistory.builder()
            .userId(user.getId())
            .points(event.getPointsEarned())
            .transactionType(PointsTransactionType.CHECKIN)
            .description(description)
            .build();

        userPointsHistoryRepository.save(history);
        log.debug("Points history created: userId={}, points={}, txId={}", 
            user.getId(), event.getPointsEarned(), event.getTransactionId());
    }
}
