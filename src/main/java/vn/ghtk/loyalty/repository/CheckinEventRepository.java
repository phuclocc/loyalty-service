package vn.ghtk.loyalty.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ghtk.loyalty.entity.CheckinEvent;

import java.util.Optional;

@Repository
public interface CheckinEventRepository extends JpaRepository<CheckinEvent, Long> {

    /**
     * Find event by eventId for deduplication
     */
    Optional<CheckinEvent> findByEventId(String eventId);

    /**
     * Check if event exists by eventId
     */
    boolean existsByEventId(String eventId);
}
