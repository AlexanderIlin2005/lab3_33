// src/main/java/org/itmo/aop/CacheLoggerAspect.java
package org.itmo.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.itmo.service.CacheStatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CacheLoggerAspect {

    private static final Logger logger = LoggerFactory.getLogger(CacheLoggerAspect.class);

    @Autowired
    private CacheStatisticsService cacheStatisticsService;

    // Pointcut для метода в контроллере, который включает/выключает логирование
    // Используем сигнатуру метода, который будет вызываться
    @Pointcut("execution(public * org.itmo.controller.CacheController.toggleStatsLoggingInternal(..)) && args(enabled, ..)")
    public void toggleStatsLoggingPointcut(boolean enabled) {}

    // Pointcut для метода в контроллере, который вызывает логирование статистики
    @Pointcut("execution(public * org.itmo.controller.CacheController.triggerLogStats(..))")
    public void logStatsPointcut() {}

    // Включение/выключение логирования через AOP
    @Before("toggleStatsLoggingPointcut(enabled)")
    public void toggleLogging(boolean enabled) {
        logger.info("AOP: Intercepted call to toggle cache stats logging to {}", enabled);
        cacheStatisticsService.setLoggingEnabled(enabled);
    }

    // Вызов логирования статистики через AOP
    @Before("logStatsPointcut()")
    public void logCacheStatistics() {
        logger.debug("AOP: Intercepted call to trigger cache stats logging.");
        cacheStatisticsService.logCurrentStats();
    }
}