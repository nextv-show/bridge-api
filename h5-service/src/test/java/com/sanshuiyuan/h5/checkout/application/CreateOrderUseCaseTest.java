package com.sanshuiyuan.h5.checkout.application;

import com.sanshuiyuan.h5.checkout.api.dto.OrderCreateResponse;
import com.sanshuiyuan.h5.checkout.domain.DeviceSpec;
import com.sanshuiyuan.h5.checkout.domain.H5Order;
import com.sanshuiyuan.h5.checkout.domain.KycRecord;
import com.sanshuiyuan.h5.checkout.domain.KycStatus;
import com.sanshuiyuan.h5.checkout.infra.repository.DeviceSpecRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.H5OrderRepository;
import com.sanshuiyuan.h5.checkout.infra.repository.KycRecordRepository;
import com.sanshuiyuan.h5.common.BizException;
import com.sanshuiyuan.h5.common.ErrorCode;
import com.sanshuiyuan.h5.referral.H5User;
import com.sanshuiyuan.h5.referral.H5UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock H5OrderRepository orderRepo;
    @Mock DeviceSpecRepository specRepo;
    @Mock KycRecordRepository kycRepo;
    @Mock H5UserRepository userRepo;

    private CreateOrderUseCase createUseCase() {
        return new CreateOrderUseCase(orderRepo, specRepo, kycRepo, userRepo);
    }

    private DeviceSpec activeSpec() {
        // Use reflection or the builder pattern — DeviceSpec is a JPA entity with protected constructor.
        // We need to construct it manually for tests.
        try {
            var ctor = DeviceSpec.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            DeviceSpec s = ctor.newInstance();
            setField(s, "specId", "home-pro");
            setField(s, "modelCode", "BR-H2");
            setField(s, "priceCents", 680000L);
            setField(s, "status", DeviceSpec.SpecStatus.ACTIVE);
            return s;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private KycRecord passKycRecord() {
        try {
            var ctor = KycRecord.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            KycRecord r = ctor.newInstance();
            setField(r, "openid", "test-openid");
            setField(r, "status", KycStatus.PASS);
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void execute_success_createsOrder() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.of(passKycRecord()));
        when(specRepo.findBySpecIdAndStatus("home-pro", DeviceSpec.SpecStatus.ACTIVE))
                .thenReturn(Optional.of(activeSpec()));
        when(orderRepo.findFirstByOpenidAndSpecIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderUseCase uc = createUseCase();
        OrderCreateResponse resp = uc.execute("openid1", "home-pro", "WX_JSAPI");

        assertThat(resp.specId()).isEqualTo("home-pro");
        assertThat(resp.amountCents()).isEqualTo(680000L);
        assertThat(resp.status()).isEqualTo("PENDING_PAY");

        ArgumentCaptor<H5Order> captor = ArgumentCaptor.forClass(H5Order.class);
        verify(orderRepo).save(captor.capture());
        H5Order saved = captor.getValue();
        assertThat(saved.getAmountCents()).isEqualTo(680000L);
        assertThat(saved.getSpecId()).isEqualTo("home-pro");
    }

    @Test
    void execute_snapshotsReferralChainOntoOrder() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.of(passKycRecord()));
        when(specRepo.findBySpecIdAndStatus("home-pro", DeviceSpec.SpecStatus.ACTIVE))
                .thenReturn(Optional.of(activeSpec()));
        when(orderRepo.findFirstByOpenidAndSpecIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));

        H5User bound = H5User.create("openid1");
        bound.bindReferral(100L, 50L); // L1=100, L2=50
        when(userRepo.findByOpenid("openid1")).thenReturn(Optional.of(bound));

        CreateOrderUseCase uc = createUseCase();
        uc.execute("openid1", "home-pro", "WX_JSAPI");

        ArgumentCaptor<H5Order> captor = ArgumentCaptor.forClass(H5Order.class);
        verify(orderRepo).save(captor.capture());
        H5Order saved = captor.getValue();
        assertThat(saved.getInviterId()).isEqualTo(100L);
        assertThat(saved.getGrandInviterId()).isEqualTo(50L);
    }

    @Test
    void execute_naturalTraffic_noReferralSnapshot() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.of(passKycRecord()));
        when(specRepo.findBySpecIdAndStatus("home-pro", DeviceSpec.SpecStatus.ACTIVE))
                .thenReturn(Optional.of(activeSpec()));
        when(orderRepo.findFirstByOpenidAndSpecIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findByOpenid("openid1")).thenReturn(Optional.empty());

        CreateOrderUseCase uc = createUseCase();
        uc.execute("openid1", "home-pro", "WX_JSAPI");

        ArgumentCaptor<H5Order> captor = ArgumentCaptor.forClass(H5Order.class);
        verify(orderRepo).save(captor.capture());
        H5Order saved = captor.getValue();
        assertThat(saved.getInviterId()).isNull();
        assertThat(saved.getGrandInviterId()).isNull();
    }

    @Test
    void execute_noKyc_throwsKycRequired() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.empty());

        CreateOrderUseCase uc = createUseCase();
        assertThatThrownBy(() -> uc.execute("openid1", "home-pro", "WX_JSAPI"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode()).isEqualTo(ErrorCode.KYC_REQUIRED));
    }

    @Test
    void execute_invalidSpec_throwsSpecNotFound() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.of(passKycRecord()));
        when(specRepo.findBySpecIdAndStatus("invalid-spec", DeviceSpec.SpecStatus.ACTIVE))
                .thenReturn(Optional.empty());

        CreateOrderUseCase uc = createUseCase();
        assertThatThrownBy(() -> uc.execute("openid1", "invalid-spec", "WX_JSAPI"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).errorCode()).isEqualTo(ErrorCode.SPEC_NOT_FOUND));
    }

    @Test
    void execute_serverSidePriceUsed_notFrontEndPrice() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.of(passKycRecord()));
        DeviceSpec spec = activeSpec();
        setField(spec, "priceCents", 999999L); // server-side price
        when(specRepo.findBySpecIdAndStatus("home-pro", DeviceSpec.SpecStatus.ACTIVE))
                .thenReturn(Optional.of(spec));
        when(orderRepo.findFirstByOpenidAndSpecIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(orderRepo.save(any(H5Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderUseCase uc = createUseCase();
        OrderCreateResponse resp = uc.execute("openid1", "home-pro", "WX_JSAPI");

        // Amount comes from server-side spec, not from any client input
        assertThat(resp.amountCents()).isEqualTo(999999L);
    }

    @Test
    void execute_pendingOrderWithin30Min_reusesExistingOrder() {
        when(kycRepo.findFirstByOpenidAndStatusOrderByVerifiedAtDesc("openid1", KycStatus.PASS))
                .thenReturn(Optional.of(passKycRecord()));
        when(specRepo.findBySpecIdAndStatus("home-pro", DeviceSpec.SpecStatus.ACTIVE))
                .thenReturn(Optional.of(activeSpec()));

        // Simulate existing pending order
        H5Order existingOrder = H5Order.create("H5EXISTING123", "openid1", "home-pro", "BR-H2", 680000L, "WX_JSAPI");
        setField(existingOrder, "id", 42L);
        when(orderRepo.findFirstByOpenidAndSpecIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                anyString(), anyString(), any(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(existingOrder));

        CreateOrderUseCase uc = createUseCase();
        OrderCreateResponse resp = uc.execute("openid1", "home-pro", "WX_JSAPI");

        assertThat(resp.orderId()).isEqualTo(42L);
        assertThat(resp.orderNo()).isEqualTo("H5EXISTING123");
        assertThat(resp.amountCents()).isEqualTo(680000L);
    }
}
