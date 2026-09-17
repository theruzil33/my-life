package com.theruzil.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.theruzil.gateway.config.GatewayProperties;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final Long USER_ID = 7L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private MutableClock clock;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        GatewayProperties properties = new GatewayProperties();
        properties.getAuth().setRefreshTokenTtl(Duration.ofDays(7));
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties, clock);
    }

    @Test
    void issueStoresOnlyHashOfRandomTokenWithUserBindingAndTtl() {
        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(USER_ID);

        assertThat(issued.value()).isNotBlank();
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));

        RefreshToken stored = captureSingleSave();
        assertThat(stored.getUserId()).isEqualTo(USER_ID);
        assertThat(stored.getTokenHash()).isNotEqualTo(issued.value());
        assertThat(stored.getTokenHash()).isEqualTo(RefreshTokenService.hash(issued.value()));
        assertThat(stored.isActive(NOW)).isTrue();
    }

    @Test
    void issueGeneratesDistinctTokenValues() {
        String first = refreshTokenService.issue(USER_ID).value();
        String second = refreshTokenService.issue(USER_ID).value();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rotateRevokesCurrentTokenAndIssuesNewOne() {
        RefreshToken current = activeToken("raw-token");
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("raw-token")))
                .thenReturn(Optional.of(current));

        RefreshTokenService.Rotation rotation = refreshTokenService.rotate("raw-token");

        assertThat(rotation.userId()).isEqualTo(USER_ID);
        assertThat(rotation.value()).isNotEqualTo("raw-token");
        assertThat(current.getRevokedAt()).isEqualTo(NOW);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        RefreshToken issued = saved.getAllValues().get(1);
        assertThat(issued.getUserId()).isEqualTo(USER_ID);
        assertThat(issued.getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.value()));
        assertThat(issued.getTokenHash()).isNotEqualTo(current.getTokenHash());
    }

    @Test
    void rotateRejectsMissingTokenWithoutTouchingRepository() {
        assertThatThrownBy(() -> refreshTokenService.rotate(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate("  ")).isInstanceOf(InvalidTokenException.class);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown")).isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshToken expired = new RefreshToken(USER_ID, RefreshTokenService.hash("expired"), NOW.minus(Duration.ofDays(8)),
                NOW.minus(Duration.ofDays(1)));
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("expired")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired")).isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRejectsAlreadyRevokedToken() {
        RefreshToken revoked = activeToken("revoked");
        revoked.revoke(NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("revoked")))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("revoked")).isInstanceOf(InvalidTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeMarksTokenAsRevoked() {
        RefreshToken current = activeToken("logout-token");
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("logout-token")))
                .thenReturn(Optional.of(current));

        refreshTokenService.revoke("logout-token");

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        verify(refreshTokenRepository).save(current);
    }

    @Test
    void revokeIgnoresMissingOrUnknownToken() {
        refreshTokenService.revoke(null);
        refreshTokenService.revoke("  ");
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());
        refreshTokenService.revoke("unknown");

        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshToken activeToken(String rawValue) {
        return new RefreshToken(USER_ID, RefreshTokenService.hash(rawValue), NOW, NOW.plus(Duration.ofDays(7)));
    }

    private RefreshToken captureSingleSave() {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        return saved.getValue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
