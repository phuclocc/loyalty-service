package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.response.CheckinResponse;

import java.time.LocalDate;

public interface CheckinTransactionService {

    CheckinResponse doCheckinTransactional(Long userId, LocalDate today);
}


