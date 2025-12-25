package com.example.foreverrip.controller;

import com.example.foreverrip.service.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/translate")
public class TranslationController {
    
    private static final Logger log = LoggerFactory.getLogger(TranslationController.class);
    private final TranslationService translationService;
    
    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }
    
    @GetMapping("/{russianWord}")
    public Mono<ResponseEntity<String>> translate(@PathVariable String russianWord) {
        log.info("🔍 Received translation request for word: {}", russianWord);
        log.info("⏱️  Starting translation at: {}", System.currentTimeMillis());
        
        return translationService.translate(russianWord)
            .map(translation -> {
                log.info("✅ Translation successful: {} -> {}", russianWord, translation);
                log.info("⏱️  Translation completed at: {}", System.currentTimeMillis());
                return ResponseEntity.ok(translation);
            })
            .onErrorResume(e -> {
                log.error("❌ Error translating word: {}", russianWord, e);
                return Mono.just(ResponseEntity
                    .badRequest()
                    .body("Error: " + e.getMessage()));
            });
    }
    
    @GetMapping("/batch")
    public Flux<String> translateBatch(@RequestParam List<String> words) {
        log.info("📦 Received batch translation request for {} words", words.size());
        return translationService.translateBatch(words);
    }
    
    @GetMapping("/health")
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("Server is running"));
    }
}