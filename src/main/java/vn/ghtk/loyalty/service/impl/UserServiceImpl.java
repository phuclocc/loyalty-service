package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ghtk.loyalty.dto.request.CreateUserRequest;
import vn.ghtk.loyalty.dto.response.UserProfileResponse;
import vn.ghtk.loyalty.dto.response.UserResponse;
import vn.ghtk.loyalty.entity.User;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.DailyCheckinRepository;
import vn.ghtk.loyalty.repository.UserRepository;
import vn.ghtk.loyalty.service.UserService;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final DailyCheckinRepository dailyCheckinRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        userRepository.findByUsername(request.getUsername()).ifPresent(u -> {
            throw new BusinessException("Username already exists");
        });

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .avatar(request.getAvatar())
                .totalPoints(0)
                .build();

        User savedUser = userRepository.save(user);

        return UserResponse.builder()
                .id(savedUser.getId())
                .name(savedUser.getName())
                .avatar(savedUser.getAvatar())
                .totalPoints(savedUser.getTotalPoints())
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        LocalDate now = LocalDate.now();
        Long totalCheckinDays = dailyCheckinRepository.countByUserIdAndMonth(
                userId, now.getYear(), now.getMonthValue()
        );

        return UserProfileResponse.builder()
                .name(user.getName())
                .avatar(user.getAvatar())
                .totalPoints(user.getTotalPoints())
                .totalCheckinDaysInMonth(totalCheckinDays)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .avatar(user.getAvatar())
                .totalPoints(user.getTotalPoints())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

