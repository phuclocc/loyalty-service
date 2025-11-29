package vn.ghtk.loyalty.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

@Slf4j
@Configuration
@Getter
public class CheckinConfig {

    @Value("${loyalty.checkin.points-sequence}")
    private String pointsSequenceStr;

    @Value("${loyalty.checkin.max-per-month}")
    private int maxPerMonth;

    private int[] pointsSequence;

    @PostConstruct
    public void init() {
        // Parse comma-separated string to int array
        this.pointsSequence = Arrays.stream(pointsSequenceStr.split(","))
                .map(String::trim)
                .mapToInt(Integer::parseInt)
                .toArray();
        
        log.info("Check-in points sequence loaded: {}", Arrays.toString(pointsSequence));
        log.info("Max check-ins per month: {}", maxPerMonth);
    }
}

