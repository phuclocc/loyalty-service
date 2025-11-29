package vn.ghtk.loyalty.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.ghtk.loyalty.entity.DailyCheckin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyCheckinRepository extends JpaRepository<DailyCheckin, Long> {

    Optional<DailyCheckin> findByUserIdAndCheckinDate(Long userId, LocalDate checkinDate);

    @Query("SELECT COUNT(d) FROM DailyCheckin d WHERE d.userId = :userId " +
           "AND YEAR(d.checkinDate) = :year AND MONTH(d.checkinDate) = :month")
    Long countByUserIdAndMonth(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT d FROM DailyCheckin d WHERE d.userId = :userId " +
           "AND YEAR(d.checkinDate) = :year AND MONTH(d.checkinDate) = :month " +
           "ORDER BY d.checkinDate DESC")
    List<DailyCheckin> findByUserIdAndMonth(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT MAX(d.checkinOrder) FROM DailyCheckin d WHERE d.userId = :userId " +
           "AND YEAR(d.checkinDate) = :year AND MONTH(d.checkinDate) = :month")
    Integer findMaxCheckinOrderByUserIdAndMonth(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);
}

