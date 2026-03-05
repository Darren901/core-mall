package com.coremall.user.service;

import com.coremall.sharedkernel.jwt.JwtTokenProvider;
import com.coremall.user.domain.User;
import com.coremall.user.dto.LoginRequest;
import com.coremall.user.dto.LoginResponse;
import com.coremall.user.dto.RegisterRequest;
import com.coremall.user.dto.RegisterResponse;
import com.coremall.user.exception.DuplicateEmailException;
import com.coremall.user.exception.InvalidCredentialsException;
import com.coremall.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }
        User user = User.of(request.email(), passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        return new RegisterResponse(saved.getId().toString(), saved.getEmail());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtTokenProvider.generateToken(user.getId().toString());
        return new LoginResponse(token, user.getId().toString());
    }
}
