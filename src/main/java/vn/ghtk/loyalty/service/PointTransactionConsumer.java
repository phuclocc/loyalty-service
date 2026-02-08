package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.event.PointTransactionEvent;

/**
 * Service for consuming PointTransactionEvent from Kafka
 * and writing to database (DB Writer)
 */
public interface PointTransactionConsumer {
    
    /**
     * Process point transaction event and write to DB
     * @param event PointTransactionEvent from Flink
     */
    void processPointTransaction(PointTransactionEvent event);
}
