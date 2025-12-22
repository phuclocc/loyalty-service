package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import vn.ghtk.loyalty.config.CheckinConfig;
import vn.ghtk.loyalty.dto.response.CheckinResponse;
import vn.ghtk.loyalty.dto.response.CheckinStatusResponse;
import vn.ghtk.loyalty.entity.DailyCheckin;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.DailyCheckinRepository;
import vn.ghtk.loyalty.service.CheckinService;
import vn.ghtk.loyalty.service.CheckinTransactionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private static final LocalTime MORNING_START = LocalTime.of(9, 0);
    private static final LocalTime MORNING_END = LocalTime.of(11, 0);
    private static final LocalTime EVENING_START = LocalTime.of(19, 0);
    private static final LocalTime EVENING_END = LocalTime.of(21, 0);

    private final DailyCheckinRepository dailyCheckinRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient redissonClient;
    private final CheckinTransactionService checkinTransactionService;
    private final CheckinConfig checkinConfig;

    @Override
    public CheckinResponse checkin(Long userId) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // Validate check-in time window
        if (!isValidCheckinTime(now)) {
            throw new BusinessException("Check-in is only allowed between 9:00-11:00 or 19:00-21:00");
        }

        String redisKey = String.format("checkin:%d:%s", userId, today.format(DateTimeFormatter.ISO_DATE));

        // Quick check Redis for early exit (Redis chỉ set sau khi DB commit thành công)
        if (redisTemplate.hasKey(redisKey)) {
            throw new BusinessException("You have already checked in today");
        }

        // Use Redisson lock to prevent concurrent check-ins
        String lockKey = String.format("lock:checkin:%d", userId);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            // Try to acquire lock with 10 seconds timeout, lease time 30s
            locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("Unable to acquire lock. Please try again.");
            }

            // Thực hiện check-in trong transaction, commit xong trước khi unlock
            CheckinResponse response = checkinTransactionService.doCheckinTransactional(userId, today);

            // Sau khi DB commit thành công, set Redis key
            long secondsUntilMidnight = java.time.Duration.between(
                    LocalDateTime.now(),
                    today.atStartOfDay().plusDays(1)
            ).getSeconds();
            redisTemplate.opsForValue().set(redisKey, "1", secondsUntilMidnight, TimeUnit.SECONDS);

            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Check-in process was interrupted");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public List<CheckinStatusResponse> getCheckinStatusForCurrentMonth(Long userId) {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        // Get all check-ins for current month
        List<DailyCheckin> checkins = dailyCheckinRepository.findByUserIdAndMonth(userId, year, month);

        // Create a map of check-in dates for quick lookup
        Map<LocalDate, DailyCheckin> checkinMap = checkins.stream()
                .collect(Collectors.toMap(DailyCheckin::getCheckinDate, Function.identity()));

        int maxPerMonth = checkinConfig.getMaxPerMonth();
        int[] pointsSequence = checkinConfig.getPointsSequence();
        int checkedInCount = checkins.size();

        List<CheckinStatusResponse> statusList = new ArrayList<>();

        // Add all checked-in days (sorted by date)
        List<DailyCheckin> sortedCheckins = checkins.stream()
                .sorted(Comparator.comparing(DailyCheckin::getCheckinDate))
                .toList();

        for (DailyCheckin checkin : sortedCheckins) {
            statusList.add(CheckinStatusResponse.builder()
                    .date(checkin.getCheckinDate())
                    .checkedIn(true)
                    .pointsEarned(checkin.getPointsEarned())
                    .build());
        }

        // Add today if not checked in yet
        boolean todayCheckedIn = checkinMap.containsKey(today);
        if (!todayCheckedIn && checkedInCount < maxPerMonth) {
            if (checkedInCount < pointsSequence.length) {
                statusList.add(CheckinStatusResponse.builder()
                        .date(today)
                        .checkedIn(false)
                        .pointsEarned(pointsSequence[checkedInCount])
                        .build());
            }
        }

        // Fill remaining slots with null dates
        while (statusList.size() < maxPerMonth) {
            int dayIndex = statusList.size(); // Index in the points sequence
            Integer pointsEarned = null;
            if (dayIndex < pointsSequence.length) {
                pointsEarned = pointsSequence[dayIndex];
            }

            statusList.add(CheckinStatusResponse.builder()
                    .date(null)
                    .checkedIn(false)
                    .pointsEarned(pointsEarned)
                    .build());
        }

        return statusList;
    }

    private boolean isValidCheckinTime(LocalTime time) {
        return (time.isAfter(MORNING_START) && time.isBefore(MORNING_END)) ||
               (time.isAfter(EVENING_START) && time.isBefore(EVENING_END)) ||
               time.equals(MORNING_START) || time.equals(MORNING_END) ||
               time.equals(EVENING_START) || time.equals(EVENING_END);
    }
}

