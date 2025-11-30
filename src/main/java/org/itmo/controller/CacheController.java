// src/main/java/org/itmo/controller/CacheController.java
package org.itmo.controller;

import org.itmo.service.CacheStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

    @Autowired
    private CacheStatisticsService cacheStatisticsService;

    // --- ВАЖНО: Self-injection для обхода проблемы внутренних вызовов в Spring AOP ---
    @Autowired
    private CacheController self;

    // Эндпоинт для включения/выключения через AOP
    @PostMapping("/stats/logging/{enable}")
    public ResponseEntity<String> toggleStatsLogging(@PathVariable boolean enable) {
        // --- ВЫЗОВ ЧЕРЕЗ SELF ---
        self.toggleStatsLoggingInternal(enable);
        String status = enable ? "enabled" : "disabled";
        return ResponseEntity.ok("Cache statistics logging " + status + " via AOP.");
    }

    // Этот метод будет перехвачен AOP
    @PostMapping("/internal/toggle") // Добавим публичный маппинг, чтобы Spring точно создал прокси
    public void toggleStatsLoggingInternal(boolean enable) {
        // AOP сработает на этом методе, потому что он вызывается как self.{method},
        // что эквивалентно межбиновому вызову через прокси.
    }

    @GetMapping("/stats/logging")
    public ResponseEntity<Boolean> getStatsLoggingStatus() {
        return ResponseEntity.ok(cacheStatisticsService.isLoggingEnabled());
    }

    // Эндпоинт для вызова логирования статистики через AOP
    // ... (оставляем как есть, но теперь он тоже может использовать self.triggerLogStats() если нужно вызывать через AOP)
    @PostMapping("/stats/current")
    public ResponseEntity<String> triggerLogStatsViaAOP() {
        logger.info("Directly calling cacheStatisticsService.logCurrentStats from controller");
        cacheStatisticsService.logCurrentStats();
        return ResponseEntity.ok("Cache statistics logging triggered via AOP (or directly if AOP failed). Check application logs.");
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CacheController.class);
}