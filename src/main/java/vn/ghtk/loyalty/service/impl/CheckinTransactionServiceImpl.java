package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.ghtk.loyalty.config.CheckinConfig;
import vn.ghtk.loyalty.dto.response.CheckinResponse;
import vn.ghtk.loyalty.entity.DailyCheckin;
import vn.ghtk.loyalty.entity.User;
import vn.ghtk.loyalty.entity.UserPointsHistory;
import vn.ghtk.loyalty.enums.PointsTransactionType;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.DailyCheckinRepository;
import vn.ghtk.loyalty.repository.UserPointsHistoryRepository;
import vn.ghtk.loyalty.repository.UserRepository;
import vn.ghtk.loyalty.service.CheckinTransactionService;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinTransactionServiceImpl implements CheckinTransactionService {

    private final UserRepository userRepository;
    private final DailyCheckinRepository dailyCheckinRepository;
    private final UserPointsHistoryRepository userPointsHistoryRepository;
    private final CheckinConfig checkinConfig;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckinResponse doCheckinTransactional(Long userId, LocalDate today) {
        // Đảm bảo theo DB: một user chỉ được check-in một lần trong ngày
        boolean existsToday = dailyCheckinRepository
                .findByUserIdAndCheckinDate(userId, today)
                .isPresent();
        if (existsToday) {
            throw new BusinessException("You have already checked in today");
        }

        // Check monthly limit
        int currentMonthCheckins = dailyCheckinRepository.countByUserIdAndMonth(
                userId, today.getYear(), today.getMonthValue()
        ).intValue();
        int maxPerMonth = checkinConfig.getMaxPerMonth();
        if (currentMonthCheckins >= maxPerMonth) {
            throw new BusinessException("Maximum " + maxPerMonth + " check-ins per month reached");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        // Calculate check-in order and points
        Integer maxOrder = dailyCheckinRepository.findMaxCheckinOrderByUserIdAndMonth(
                userId, today.getYear(), today.getMonthValue()
        );
        int checkinOrder = (maxOrder == null ? 0 : maxOrder) + 1;
        int[] pointsSequence = checkinConfig.getPointsSequence();
        int pointsEarned = pointsSequence[checkinOrder - 1];

        // Save check-in record
        DailyCheckin checkin = DailyCheckin.builder()
                .userId(userId)
                .checkinDate(today)
                .pointsEarned(pointsEarned)
                .checkinOrder(checkinOrder)
                .build();
        dailyCheckinRepository.save(checkin);

        // Update user points
        user.setTotalPoints(user.getTotalPoints() + pointsEarned);
        userRepository.save(user);

        // Save points history
        UserPointsHistory history = UserPointsHistory.builder()
                .userId(userId)
                .points(pointsEarned)
                .transactionType(PointsTransactionType.CHECKIN)
                .description(String.format("Daily check-in #%d", checkinOrder))
                .build();
        userPointsHistoryRepository.save(history);

        log.info("User {} checked in successfully. Points earned: {}, Order: {}", userId, pointsEarned, checkinOrder);

        return CheckinResponse.builder()
                .success(true)
                .message("Check-in successful")
                .pointsEarned(pointsEarned)
                .totalPoints(user.getTotalPoints())
                .checkinOrder(checkinOrder)
                .build();
    }
}


