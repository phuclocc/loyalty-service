package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import vn.ghtk.loyalty.dto.event.CheckinEvent;
import vn.ghtk.loyalty.service.CheckinEventProducer;

import java.util.concurrent.CompletableFuture;

/**
 * Implementation of CheckinEventProducer
 * Publishes check-in events to Kafka with idempotent producer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinEventProducerImpl implements CheckinEventProducer {

    private final KafkaTemplate<String, CheckinEvent> kafkaTemplate;

    @Value("${spring.kafka.topic.checkin:loyalty.checkin}")
    private String checkinTopic;

    @Override
    public boolean publishCheckinEvent(CheckinEvent event) {
        try {
            String key = event.getEventId(); // Use eventId as message key for ordering
            
            log.info("Publishing check-in event to Kafka: eventId={}, userId={}, topic={}", 
                event.getEventId(), event.getUserId(), checkinTopic);
            
            CompletableFuture<SendResult<String, CheckinEvent>> future = 
                kafkaTemplate.send(checkinTopic, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Successfully published event: eventId={}, partition={}, offset={}", 
                        event.getEventId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish event: eventId={}, error={}", 
                        event.getEventId(), ex.getMessage(), ex);
                }
            });
            
            // Wait for the result (synchronous mode for reliability)
            SendResult<String, CheckinEvent> result = future.get();
            return result != null;
            
        } catch (Exception e) {
            log.error("Error publishing check-in event: eventId={}, error={}", 
                event.getEventId(), e.getMessage(), e);
            return false;
        }
    }
}
