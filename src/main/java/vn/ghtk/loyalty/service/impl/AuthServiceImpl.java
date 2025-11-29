package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.ghtk.loyalty.dto.request.LoginRequest;
import vn.ghtk.loyalty.dto.response.LoginResponse;
import vn.ghtk.loyalty.entity.User;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.UserRepository;
import vn.ghtk.loyalty.service.AuthService;
import vn.ghtk.loyalty.util.JwtUtil;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(String.valueOf(user.getId()));

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMillis())
                .build();
    }
}

