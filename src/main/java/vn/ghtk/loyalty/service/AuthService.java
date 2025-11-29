package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.request.LoginRequest;
import vn.ghtk.loyalty.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}

