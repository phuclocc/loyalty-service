package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.response.CheckinResponse;

import java.time.LocalDate;

/**
 * Transactional boundary for check-in business logic.
 * This is separated to avoid @Transactional self-invocation issues.
 */
public interface CheckinTransactionService {

    CheckinResponse doCheckinTransactional(Long userId, LocalDate today);
}


