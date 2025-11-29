package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.request.DeductPointsRequest;

public interface PointsTransactionService {
    void doDeductPointsTransactional(Long userId, DeductPointsRequest request);
}

