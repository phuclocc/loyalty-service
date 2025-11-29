package vn.ghtk.loyalty.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ghtk.loyalty.entity.UserPointsHistory;

import java.time.LocalDateTime;

@Repository
public interface UserPointsHistoryRepository extends JpaRepository<UserPointsHistory, Long> {

    Page<UserPointsHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT h FROM UserPointsHistory h WHERE h.userId = :userId " +
           "AND YEAR(h.createdAt) = :year AND MONTH(h.createdAt) = :month " +
           "ORDER BY h.createdAt DESC")
    Page<UserPointsHistory> findByUserIdAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month,
            Pageable pageable
    );
}

