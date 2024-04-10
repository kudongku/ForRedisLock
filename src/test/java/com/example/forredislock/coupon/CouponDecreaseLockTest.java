package com.example.forredislock.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.forredislock.coupon.entity.Coupon;
import com.example.forredislock.coupon.repository.CouponRepository;
import com.example.forredislock.coupon.service.CouponDecreaseService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // 테스트 인스턴스의 생성 단위를 클래스로 변경합니다. 테그트 메소드가 필드를 공유함
@SpringBootTest
public class CouponDecreaseLockTest {

    @Autowired
    CouponRepository couponRepository;
    @Autowired
    CouponDecreaseService couponDecreaseService;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        coupon = new Coupon("KURLY_001", 100L);
        couponRepository.save(coupon);
    }

    /**
     * Feature: 쿠폰 차감 동시성 테스트 Background Given KURLY_001 라는 이름의 쿠폰 100장이 등록되어 있음
     * <p>
     * Scenario: 100장의 쿠폰을 100명의 사용자가 동시에 접근해 발급 요청함 Lock의 이름은 쿠폰명으로 설정함
     * <p>
     * Then 사용자들의 요청만큼 정확히 쿠폰의 개수가 차감되어야 함
     */
    @Test
    void 쿠폰차감_분산락_적용_동시성100명_테스트() throws InterruptedException {
        int numberOfThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    // 분산락 적용 메서드 호출 (락의 key는 쿠폰의 name으로 설정)
                    couponDecreaseService.couponDecrease(coupon.getName(), coupon.getId());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Coupon persistCoupon = couponRepository.findById(coupon.getId())
            .orElseThrow(IllegalArgumentException::new);

        assertThat(persistCoupon.getAvailableStock()).isZero();
        System.out.println("잔여 쿠폰 개수 = " + persistCoupon.getAvailableStock());
    }
}
