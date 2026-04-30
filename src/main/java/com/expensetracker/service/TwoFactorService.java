package com.expensetracker.service;

import com.expensetracker.domain.entity.TwoFactorChallenge;
import com.expensetracker.domain.entity.User;
import com.expensetracker.dto.response.TotpSetupResponse;
import com.expensetracker.exception.AuthException;
import com.expensetracker.repository.TwoFactorChallengeRepository;
import com.expensetracker.repository.UserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

@Service
@Slf4j
@RequiredArgsConstructor
public class TwoFactorService {

    private final TwoFactorChallengeRepository challengeRepo;
    private final UserRepository userRepository;
    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;

    // ── TOTP setup ──────────────────────────────────────────────────────────

    @Transactional
    public TotpSetupResponse setupTotp(User user) {
        String secret = secretGenerator.generate();

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("SmartExpense")
                .digits(6)
                .period(30)
                .build();

        String qrDataUri;
        try {
            qrDataUri = getDataUriForImage(qrGenerator.generate(qrData), qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }

        user.setTotpSecret(secret);
        user.setTotpEnabled(false);
        userRepository.save(user);

        return new TotpSetupResponse(secret, qrDataUri);
    }

    @Transactional
    public void confirmTotpSetup(User user, String code) {
        if (user.getTotpSecret() == null) {
            throw new AuthException("TOTP setup not initiated. Please start setup first.");
        }
        if (!codeVerifier.isValidCode(user.getTotpSecret(), code)) {
            throw new AuthException("Invalid TOTP code. Please try again.");
        }
        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void disableTotp(User user) {
        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        userRepository.save(user);
    }

    // ── Challenge lifecycle ─────────────────────────────────────────────────

    @Transactional
    public TwoFactorChallenge createChallenge(User user) {
        challengeRepo.deleteByUserIdAndUsedFalse(user.getId());
        return challengeRepo.save(TwoFactorChallenge.builder()
                .user(user)
                .codeHash("")
                .method("TOTP")
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build());
    }

    @Transactional
    public User verifyChallenge(String challengeId, String code) {
        UUID id;
        try {
            id = UUID.fromString(challengeId);
        } catch (IllegalArgumentException e) {
            throw new AuthException("Invalid challenge.");
        }

        TwoFactorChallenge challenge = challengeRepo.findByIdAndUsedFalse(id)
                .orElseThrow(() -> new AuthException("Invalid or already used challenge."));

        if (challenge.isExpired()) {
            throw new AuthException("Challenge expired. Please log in again.");
        }

        User user = challenge.getUser();
        if (!codeVerifier.isValidCode(user.getTotpSecret(), code)) {
            throw new AuthException("Invalid authenticator code.");
        }

        challenge.setUsed(true);
        challengeRepo.save(challenge);
        return user;
    }
}
