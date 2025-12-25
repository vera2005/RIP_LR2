package com.example.foreverrip.controller;

import com.example.foreverrip.service.TranslationClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/client")
public class ClientController {
    
    private static final Logger log = LoggerFactory.getLogger(ClientController.class);
    private final TranslationClientService translationService;
    
    public ClientController(TranslationClientService translationService) {
        this.translationService = translationService;
    }
    
    @GetMapping("/translate/{word}")
    public Mono<String> translate(@PathVariable String word) {
        log.info("API: Received request to translate word: {}", word);
        return translationService.translateWord(word);
    }
    
    @GetMapping("/translate/batch")
    public Flux<String> translateBatch(@RequestParam String words) {
        List<String> wordList = Arrays.asList(words.split(","));
        log.info("API: Batch translation request for words: {}", wordList);
        return translationService.translateWords(wordList);
    }
    
    @GetMapping("/health")
    public Mono<String> health() {
        log.info("API: Health check requested");
        return translationService.checkHealth();
    }
    
    @GetMapping("/test")
    public Flux<String> test() {
        log.info("API: Test endpoint called");
        List<String> testWords = Arrays.asList("привет", "мир", "дом", "кот", "собака");
        return translationService.translateWords(testWords);
    }
}