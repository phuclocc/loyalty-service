package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.ghtk.loyalty.dto.request.DeductPointsRequest;
import vn.ghtk.loyalty.entity.User;
import vn.ghtk.loyalty.entity.UserPointsHistory;
import vn.ghtk.loyalty.enums.PointsTransactionType;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.UserPointsHistoryRepository;
import vn.ghtk.loyalty.repository.UserRepository;
import vn.ghtk.loyalty.service.PointsTransactionService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsTransactionServiceImpl implements PointsTransactionService {

    private final UserRepository userRepository;
    private final UserPointsHistoryRepository userPointsHistoryRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void doDeductPointsTransactional(Long userId, DeductPointsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        int pointsToDeduct = request.getPoints();

        if (user.getTotalPoints() < pointsToDeduct) {
            throw new BusinessException("Insufficient points. Current points: " + user.getTotalPoints());
        }

        // Deduct points
        user.setTotalPoints(user.getTotalPoints() - pointsToDeduct);
        userRepository.save(user);

        // Save transaction history
        UserPointsHistory history = UserPointsHistory.builder()
                .userId(userId)
                .points(-pointsToDeduct)
                .transactionType(PointsTransactionType.DEDUCT)
                .description("Points deduction")
                .build();
        userPointsHistoryRepository.save(history);

        log.info("Deducted {} points from user {}. Remaining points: {}", 
                pointsToDeduct, userId, user.getTotalPoints());
    }
}

