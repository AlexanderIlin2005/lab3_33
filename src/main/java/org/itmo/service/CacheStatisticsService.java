// src/main/java/org/itmo/service/CacheStatisticsService.java
package org.itmo.service;

import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManagerFactory;

@Service
public class CacheStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(CacheStatisticsService.class);
    private volatile boolean loggingEnabled = false;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        if (enabled) {
            logger.info("Cache statistics logging ENABLED.");
        } else {
            logger.info("Cache statistics logging DISABLED.");
        }
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void logCurrentStats() {
        if (!loggingEnabled) {
            logger.debug("Cache statistics logging is disabled.");
            return;
        }

        try {
            Statistics stats = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();

            logger.info("--- L2 Cache Statistics (Hibernate 6 - Global Only) ---");
            logger.info("Total Puts: {}", stats.getSecondLevelCachePutCount());
            logger.info("Total Hits: {}", stats.getSecondLevelCacheHitCount());
            logger.info("Total Misses: {}", stats.getSecondLevelCacheMissCount());
            logger.info("Region Names: {}", String.join(", ", stats.getSecondLevelCacheRegionNames()));
            logger.info("----------------------------------------");

        } catch (Exception e) {
            logger.error("Failed to retrieve L2 cache statistics via Hibernate", e);
        }
    }
}