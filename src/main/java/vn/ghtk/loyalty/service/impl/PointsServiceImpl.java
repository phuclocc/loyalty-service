package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ghtk.loyalty.dto.request.DeductPointsRequest;
import vn.ghtk.loyalty.dto.response.PageResponse;
import vn.ghtk.loyalty.dto.response.PointsHistoryResponse;
import vn.ghtk.loyalty.entity.User;
import vn.ghtk.loyalty.entity.UserPointsHistory;
import vn.ghtk.loyalty.enums.PointsTransactionType;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.UserPointsHistoryRepository;
import vn.ghtk.loyalty.repository.UserRepository;
import vn.ghtk.loyalty.service.PointsService;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private final UserRepository userRepository;
    private final UserPointsHistoryRepository userPointsHistoryRepository;

    @Override
    @Transactional
    public void deductPoints(Long userId, DeductPointsRequest request) {
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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PointsHistoryResponse> getPointsHistory(Long userId, Integer page, Integer size, Integer month) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserPointsHistory> historyPage;

        if (month != null) {
            LocalDate now = LocalDate.now();
            int year = now.getYear();
            historyPage = userPointsHistoryRepository.findByUserIdAndMonth(userId, year, month, pageable);
        } else {
            historyPage = userPointsHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        List<PointsHistoryResponse> content = historyPage.getContent().stream()
                .map(h -> PointsHistoryResponse.builder()
                        .id(h.getId())
                        .points(h.getPoints())
                        .transactionType(h.getTransactionType())
                        .description(h.getDescription())
                        .createdAt(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PageResponse.<PointsHistoryResponse>builder()
                .content(content)
                .page(historyPage.getNumber())
                .size(historyPage.getSize())
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .first(historyPage.isFirst())
                .last(historyPage.isLast())
                .build();
    }
}

