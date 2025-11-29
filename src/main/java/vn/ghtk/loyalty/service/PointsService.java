package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.request.DeductPointsRequest;
import vn.ghtk.loyalty.dto.response.PageResponse;
import vn.ghtk.loyalty.dto.response.PointsHistoryResponse;

public interface PointsService {

    void deductPoints(Long userId, DeductPointsRequest request);

    PageResponse<PointsHistoryResponse> getPointsHistory(Long userId, Integer page, Integer size, Integer month);
}

