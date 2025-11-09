package com.example.userservice.service;

import com.example.userservice.client.AggregationServiceClient;
import com.example.userservice.dto.*;
import com.example.userservice.entity.User;
import com.example.userservice.exception.InvalidCredentialsException;
import com.example.userservice.exception.UserAlreadyExistsException;
import com.example.userservice.entity.enums.VerificationStatus;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final AggregationServiceClient aggregationServiceClient;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setFullName(request.getFullName());
        user.setBankClientId(request.getClientId());
        user.setVerificationToken(UUID.randomUUID().toString());
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setVerified(false);

        User savedUser = userRepository.save(user);

        BankVerificationResult verificationResult = verifyUserInBanks(savedUser);

        emailVerificationService.sendVerificationEmail(savedUser);

        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .user(mapToProfile(savedUser))
                .bankVerification(verificationResult)
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new InvalidCredentialsException("Account is deactivated");
        }

        refreshVerificationStatus(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .user(mapToProfile(user))
                .build();
    }

    private BankVerificationResult verifyUserInBanks(User user) {
        try {

            BankVerifyResponse response = aggregationServiceClient.verifyClient(user.getBankClientId());

            updateUserVerificationStatus(user, response);

            BankVerificationResult result = mapToBankVerificationResult(response);


            return result;

        } catch (Exception e) {

            user.setVerificationStatus(VerificationStatus.ERROR);
            userRepository.save(user);

            return BankVerificationResult.builder()
                    .overallStatus(VerificationStatus.ERROR)
                    .message("Ошибка при подключении банков: " + e.getMessage())
                    .verifiedAccountsCount(0)
                    .requiresUserAction(false)
                    .nextSteps("Попробуйте позже или обратитесь в поддержку")
                    .build();
        }
    }

    private BankVerificationResult mapToBankVerificationResult(BankVerifyResponse response) {
        List<PendingBankAction> pendingActions = response.getPendingBanks().stream()
                .map(pendingBank -> PendingBankAction.builder()
                        .bankCode(pendingBank.getBankCode())
                        .bankName(pendingBank.getBankName())
                        .requestId(pendingBank.getRequestId())
                        .actionMessage(pendingBank.getMessage())
                        .actionType(pendingBank.getActionRequired())
                        .instructions(generateInstructions(pendingBank))
                        .build())
                .collect(Collectors.toList());

        String nextSteps = generateNextSteps(response.getStatus(), pendingActions);

        return BankVerificationResult.builder()
                .overallStatus(response.getStatus())
                .message(response.getMessage())
                .verifiedAccountsCount(response.getAccountsCount())
                .pendingActions(pendingActions)
                .requiresUserAction(response.isRequiresUserAction())
                .nextSteps(nextSteps)
                .build();
    }

    private String generateInstructions(PendingBank pendingBank) {
        switch (pendingBank.getActionRequired()) {
            case "need_app_approval":
                return String.format(
                        "1. Откройте приложение '%s'\n" +
                                "2. Перейдите в раздел 'Запросы доступа'\n" +
                                "3. Найдите запрос с ID: %s\n" +
                                "4. Нажмите 'Подтвердить'\n" +
                                "5. Счета появятся автоматически в течение 5 минут",
                        pendingBank.getBankName(), pendingBank.getRequestId()
                );
            case "need_sms_verification":
                return String.format(
                        "1. Дождитесь SMS от банка '%s'\n" +
                                "2. Введите код из SMS в нашем приложении\n" +
                                "3. Или подтвердите в приложении банка",
                        pendingBank.getBankName()
                );
            default:
                return "Следуйте инструкциям в приложении банка";
        }
    }


    private String generateNextSteps(VerificationStatus status, List<PendingBankAction> pendingActions) {
        if (status == VerificationStatus.VERIFIED) {
            return "Все банки подключены! Вы можете начать использование сервиса.";
        } else if (status == VerificationStatus.PARTIALLY_VERIFIED) {
            return String.format(
                    "Часть счетов уже доступна. Для полного доступа выполните действия в %d банках.",
                    pendingActions.size()
            );
        } else if (status == VerificationStatus.PENDING_ACTION) {
            return "Требуются действия в подключенных банках. Следуйте инструкциям ниже.";
        } else {
            return "Начался процесс подключения банков. Следуйте инструкциям.";
        }
    }

    private void refreshVerificationStatus(User user) {
        try {
            BankVerifyResponse response = aggregationServiceClient.checkVerificationStatus(user.getBankClientId());
            updateUserVerificationStatus(user, response);
        } catch (Exception e) {
            log.warn("Failed to refresh verification status for user: {}", user.getEmail());
        }
    }

    private void updateUserVerificationStatus(User user, BankVerifyResponse response) {
        user.setVerificationStatus(response.getStatus());

        boolean isVerified = response.getStatus() == VerificationStatus.VERIFIED ||
                response.getStatus() == VerificationStatus.PARTIALLY_VERIFIED;
        user.setVerified(isVerified);

        userRepository.save(user);

    }

    private UserProfile mapToProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setEmail(user.getEmail());
        profile.setPhone(user.getPhone());
        profile.setFullName(user.getFullName());
        profile.setBankClientId(user.getBankClientId());
        profile.setVerified(user.isVerified());
        profile.setVerificationStatus(user.getVerificationStatus());
        profile.setCreatedAt(user.getCreatedAt());
        return profile;
    }
}