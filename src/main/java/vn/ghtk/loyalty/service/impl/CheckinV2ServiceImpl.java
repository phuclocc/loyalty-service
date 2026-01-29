package vn.ghtk.loyalty.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.ghtk.loyalty.dto.event.CheckinEvent;
import vn.ghtk.loyalty.dto.event.EventMetadata;
import vn.ghtk.loyalty.dto.request.CheckinV2Request;
import vn.ghtk.loyalty.dto.response.CheckinV2Response;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.service.CheckinEventProducer;
import vn.ghtk.loyalty.service.CheckinV2Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Implementation of CheckinV2Service
 * Validates check-in rules and publishes event to Kafka
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinV2ServiceImpl implements CheckinV2Service {

    private static final LocalTime MORNING_START = LocalTime.of(0, 30);
    private static final LocalTime MORNING_END = LocalTime.of(11, 0);
    private static final LocalTime EVENING_START = LocalTime.of(19, 0);
    private static final LocalTime EVENING_END = LocalTime.of(23, 0);

    private final CheckinEventProducer checkinEventProducer;

    @Override
    public CheckinV2Response checkin(Long userId, CheckinV2Request request, HttpServletRequest httpRequest) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalDateTime eventTimestamp = LocalDateTime.now();

        // Validate check-in time window
        if (!isValidCheckinTime(now)) {
            throw new BusinessException("Check-in is only allowed between 9:00-11:00 or 19:00-21:00");
        }

        // Generate unique event ID for deduplication
        String eventId = generateEventId(userId, today, eventTimestamp);
        String yearMonth = today.format(DateTimeFormatter.ofPattern("yyyyMM"));

        // Build event metadata
        EventMetadata metadata = EventMetadata.builder()
                .source("loyalty-service")
                .version("2.0")
                .requestId(request != null && request.getRequestId() != null 
                    ? request.getRequestId() 
                    : UUID.randomUUID().toString())
                .clientIp(getClientIp(httpRequest))
                .userAgent(httpRequest != null ? httpRequest.getHeader("User-Agent") : null)
                .build();

        // Build check-in event
        CheckinEvent event = CheckinEvent.builder()
                .eventId(eventId)
                .userId(userId)
                .checkinDate(today)
                .eventTimestamp(eventTimestamp)
                .yearMonth(yearMonth)
                .metadata(metadata)
                .build();

        // Publish event to Kafka
        boolean published = checkinEventProducer.publishCheckinEvent(event);

        if (!published) {
            throw new BusinessException("Failed to publish check-in event. Please try again.");
        }

        log.info("Check-in event published successfully: eventId={}, userId={}", eventId, userId);

        return CheckinV2Response.builder()
                .eventId(eventId)
                .success(true)
                .message("Check-in event published successfully. Points will be credited shortly.")
                .eventTimestamp(eventTimestamp.toString())
                .build();
    }

    /**
     * Generate unique event ID
     * Format: {userId}_{yyyyMMdd}_{timestamp}_{random}
     */
    private String generateEventId(Long userId, LocalDate date, LocalDateTime timestamp) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timestampStr = String.valueOf(timestamp.toEpochSecond(java.time.ZoneOffset.UTC));
        String random = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%d_%s_%s_%s", userId, dateStr, timestampStr, random);
    }

    /**
     * Validate check-in time window
     */
    private boolean isValidCheckinTime(LocalTime time) {
        return (time.isAfter(MORNING_START) && time.isBefore(MORNING_END)) ||
               (time.isAfter(EVENING_START) && time.isBefore(EVENING_END)) ||
               time.equals(MORNING_START) || time.equals(MORNING_END) ||
               time.equals(EVENING_START) || time.equals(EVENING_END);
    }

    /**
     * Get client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // If multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}
