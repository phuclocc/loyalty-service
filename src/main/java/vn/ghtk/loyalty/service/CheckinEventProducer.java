package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.event.CheckinEvent;

/**
 * Service interface for publishing check-in events to Kafka
 */
public interface CheckinEventProducer {
    
    /**
     * Publish check-in event to Kafka
     * 
     * @param event CheckinEvent to publish
     * @return true if published successfully, false otherwise
     */
    boolean publishCheckinEvent(CheckinEvent event);
}
