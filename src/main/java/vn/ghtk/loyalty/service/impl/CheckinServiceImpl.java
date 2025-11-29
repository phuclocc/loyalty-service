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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

        // Quick check Redis, nhưng luôn xác nhận lại với DB để tránh dirty key
        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            boolean existsInDb = dailyCheckinRepository
                    .findByUserIdAndCheckinDate(userId, today)
                    .isPresent();
            if (existsInDb) {
                throw new BusinessException("You have already checked in today");
            } else {
                // Redis có key nhưng DB không có record → lần trước lỗi giữa chừng, xoá key để cho phép check-in lại
                redisTemplate.delete(redisKey);
            }
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

            // Sau khi DB commit thành công, set Redis key (chỉ là cache)
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
        Set<LocalDate> checkinDates = checkins.stream()
                .map(DailyCheckin::getCheckinDate)
                .collect(Collectors.toSet());

        // Count how many days already checked in
        int checkedInCount = checkins.size();

        // Get points sequence from config
        int[] pointsSequence = checkinConfig.getPointsSequence();

        List<CheckinStatusResponse> statusList = new ArrayList<>();

        // Add already checked-in days
        for (DailyCheckin checkin : checkins) {
            CheckinStatusResponse status = CheckinStatusResponse.builder()
                    .date(checkin.getCheckinDate())
                    .checkedIn(true)
                    .pointsEarned(checkin.getPointsEarned())
                    .build();
            statusList.add(status);
        }

        // If not enough days (based on max-per-month), add future days starting from today (or tomorrow if today already checked in)
        int maxPerMonth = checkinConfig.getMaxPerMonth();
        if (checkedInCount < maxPerMonth) {
            LocalDate currentDate = today;
            // If today already checked in, start from tomorrow
            if (checkinDates.contains(today)) {
                currentDate = today.plusDays(1);
            }
            
            int daysToAdd = maxPerMonth - checkedInCount;
            int addedCount = 0;

            for (int i = 0; i < daysToAdd && addedCount < daysToAdd; i++) {
                // Stop if we exceed current month
                if (currentDate.getMonthValue() != month) {
                    break;
                }

                // Skip if this date is already in the list (already checked in)
                if (!checkinDates.contains(currentDate)) {
                    int dayIndex = checkedInCount + addedCount; // Index in the points sequence
                    // Ensure index doesn't exceed points sequence length
                    if (dayIndex >= pointsSequence.length) {
                        break;
                    }
                    int pointsEarned = pointsSequence[dayIndex];

                    CheckinStatusResponse status = CheckinStatusResponse.builder()
                            .date(currentDate)
                            .checkedIn(false)
                            .pointsEarned(pointsEarned)
                            .build();
                    statusList.add(status);
                    addedCount++;
                }

                currentDate = currentDate.plusDays(1);
            }
        }

        // Sort by date to ensure chronological order
        statusList.sort(Comparator.comparing(CheckinStatusResponse::getDate));

        return statusList;
    }

    private boolean isValidCheckinTime(LocalTime time) {
        return (time.isAfter(MORNING_START) && time.isBefore(MORNING_END)) ||
               (time.isAfter(EVENING_START) && time.isBefore(EVENING_END)) ||
               time.equals(MORNING_START) || time.equals(MORNING_END) ||
               time.equals(EVENING_START) || time.equals(EVENING_END);
    }
}

