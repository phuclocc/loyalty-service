package vn.ghtk.loyalty.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Point transaction event - event từ Flink về loyalty-service
 * Sau khi Flink xử lý dedup + apply rule, publish event này lên Kafka
 * loyalty-service consume event này và ghi vào DB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionEvent {
    
    /**
     * Transaction ID - unique cho deduplication tại DB
     * Format: {userId}_{yyyyMMdd}_{checkinOrder}_{timestamp}
     */
    private String transactionId;
    
    /**
     * Original event ID from raw check-in event
     */
    private String eventId;
    
    /**
     * User ID
     */
    private Long userId;
    
    /**
     * Check-in date
     */
    private LocalDate checkinDate;
    
    /**
     * Year-month (yyyyMM) for partitioning
     */
    private String yearMonth;
    
    /**
     * Points earned for this check-in
     */
    private Integer pointsEarned;
    
    /**
     * Check-in order within the month (1-7)
     */
    private Integer checkinOrder;
    
    /**
     * Event timestamp from original check-in
     */
    private LocalDateTime eventTimestamp;
    
    /**
     * Processed timestamp by Flink
     */
    private LocalDateTime processedAt;
    
    /**
     * Metadata
     */
    private EventMetadata metadata;
}
