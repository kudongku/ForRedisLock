![](https://velog.velcdn.com/images/kudongku/post/20b99511-6a7f-44db-9630-bc64a2ad139b/image.png)

# RedisConfig
```java
/*
 * RedissonClient Configuration
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private static final String REDISSON_HOST_PREFIX = "redis://";

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(REDISSON_HOST_PREFIX + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}
```
RedissonClient을 bean으로 등록해준다.
이 등록된 bean은 AOP에서 사용된다.

---

# @DistributedLock

```java
/**
 * Redisson Distributed Lock annotation
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    /**
     * 락의 이름
     */
    String key();

    /**
     * 락의 시간 단위
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 락을 기다리는 시간 (default - 5s) 락 획득을 위해 waitTime 만큼 대기한다
     */
    long waitTime() default 5L;

    /**
     * 락 임대 시간 (default - 3s) 락을 획득한 이후 leaseTime 이 지나면 락을 해제한다
     */
    long leaseTime() default 3L;
}
```
어노테이션을 만들어서 이 어노테이션이 사용된 메소드에서 Redis 분산락 AOP를 적용할 수 있게되었다.

---

# DistributedLockAop

```java
/**
 * DistributedLock annotation 선언 시 수행되는 Aop class
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAop {
    private static final String REDISSON_LOCK_PREFIX = "LOCK:";

    private final RedissonClient redissonClient;
    private final AopForTransaction aopForTransaction;

    @Around("@annotation(com.example.forredislock.annotation.DistributedLock)")
    public Object lock(final ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);
        String key = REDISSON_LOCK_PREFIX + CustomSpringELParser.getDynamicValue(signature.getParameterNames(), joinPoint.getArgs(), distributedLock.key());
        RLock rLock = redissonClient.getLock(key);  // (1)

        try {

            boolean available = rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());  // (2)

            if (!available) {
                return false;
            }

            return aopForTransaction.proceed(joinPoint);  // (3)

        } catch (InterruptedException e) {

            throw new InterruptedException();

        } finally {

            try {

                rLock.unlock();   // (4)

            } catch (IllegalMonitorStateException e) {

                log.info("Redisson Lock Already UnLock, serviceName : "
                    + method.getName()
                    + ", key :"
                    + key
                );

            }

        }
    }
}
```
실질적으로 분산락이 적용되는 AOP 코드이다.


---

# AopForTransaction
```java
/**
 * AOP에서 트랜잭션 분리를 위한 클래스
 */
@Component
public class AopForTransaction {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object proceed(final ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }
}
```

이 클래스를 통해 트랜잭션이 끝난 후에 락이 해제되도록
셜정을 하였다. 
이렇게 될 경우, 동시성 환경에서 데이터의 정합성을 보장할 수 있다.



> #### 참조
https://helloworld.kurly.com/blog/distributed-redisson-lock/
https://github.com/kudongku/ForRedisLock
