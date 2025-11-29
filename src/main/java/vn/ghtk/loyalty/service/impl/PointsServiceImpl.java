package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ghtk.loyalty.dto.request.DeductPointsRequest;
import vn.ghtk.loyalty.dto.response.PageResponse;
import vn.ghtk.loyalty.dto.response.PointsHistoryResponse;
import vn.ghtk.loyalty.entity.UserPointsHistory;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.UserPointsHistoryRepository;
import vn.ghtk.loyalty.service.PointsService;
import vn.ghtk.loyalty.service.PointsTransactionService;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private final UserPointsHistoryRepository userPointsHistoryRepository;
    private final RedissonClient redissonClient;
    private final PointsTransactionService pointsTransactionService;

    @Override
    public void deductPoints(Long userId, DeductPointsRequest request) {

        // Use Redisson distributed lock to prevent concurrent deduction
        String lockKey = String.format("lock:points:deduct:%d", userId);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            // Try to acquire lock with 5 seconds timeout, lease time 20s
            locked = lock.tryLock(5, 20, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("Unable to acquire lock. Please try again.");
            }

            // Perform deduction in transaction
            pointsTransactionService.doDeductPointsTransactional(userId, request);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Points deduction process was interrupted");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
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

