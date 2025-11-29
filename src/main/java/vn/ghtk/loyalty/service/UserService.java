package vn.ghtk.loyalty.service;

import vn.ghtk.loyalty.dto.request.CreateUserRequest;
import vn.ghtk.loyalty.dto.response.UserProfileResponse;
import vn.ghtk.loyalty.dto.response.UserResponse;

public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserProfileResponse getUserProfile(Long userId);

    UserResponse getUserById(Long userId);
}

