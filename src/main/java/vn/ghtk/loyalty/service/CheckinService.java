package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.response.CheckinResponse;
import vn.ghtk.loyalty.dto.response.CheckinStatusResponse;

import java.util.List;

public interface CheckinService {

    CheckinResponse checkin(Long userId);

    List<CheckinStatusResponse> getCheckinStatusForCurrentMonth(Long userId);
}

