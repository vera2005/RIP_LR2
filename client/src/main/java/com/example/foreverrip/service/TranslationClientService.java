package com.example.foreverrip.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
public class TranslationClientService {
    
    private static final Logger log = LoggerFactory.getLogger(TranslationClientService.class);
    private final WebClient webClient;
    
    public TranslationClientService(WebClient webClient) {
        this.webClient = webClient;
    }
    
    public Mono<String> translateWord(String russianWord) {
        log.info("Sending translation request for: {}", russianWord);
        
        return webClient.get()
            .uri("/api/translate/{word}", russianWord)  // ← Путь без /server
            .retrieve()
            .bodyToMono(String.class)
            .doOnSuccess(translation -> 
                log.info("Translation received: {} -> {}", russianWord, translation))
            .doOnError(error -> 
                log.error("Failed to translate '{}': {}", russianWord, error.getMessage()))
            .onErrorResume(error -> 
                Mono.just("ERROR: Failed to translate - " + error.getMessage()));
    }
    
    public Flux<String> translateWords(List<String> words) {
        log.info("Starting batch translation of {} words", words.size());
        
        return Flux.fromIterable(words)
            .flatMap(this::translateWord)
            .collectList()
            .flatMapMany(translations -> {
                log.info("Batch translation completed. Processing {} results", translations.size());
                return Flux.fromIterable(translations)
                    .map(String::toUpperCase)
                    .map(String::toLowerCase)
                    .distinct()
                    .sort();
            })
            .doOnComplete(() -> log.info("Batch translation fully completed"))
            .doOnError(error -> log.error("Batch translation failed: {}", error.getMessage()));
    }
    
    public Mono<String> checkHealth() {
        log.info("Checking server health...");
        
        return webClient.get()
            .uri("/api/translate/health")  // ← Путь без /server
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(3))
            .onErrorResume(error -> Mono.just("Server is unavailable: " + error.getMessage()));
    }
}