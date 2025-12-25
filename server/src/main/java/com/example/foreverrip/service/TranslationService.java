package com.example.foreverrip.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TranslationService {
    
    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    
    // ============================
    // НЕОПТИМАЛЬНАЯ ЛОГИКА НАЧИНАЕТСЯ ЗДЕСЬ
    // ============================
    
    /**
     * Перевод одного слова с русского на английский
     * НЕОПТИМАЛЬНОСТИ:
     * 1. Чтение всего файла при каждом запросе
     * 2. Сложные regex с lookaround
     * 3. Тяжелая Unicode нормализация
     */
    public Mono<String> translate(String russianWord) {
        return Mono.fromCallable(() -> {
            log.info("START translation for: {}", russianWord);
            long startTime = System.currentTimeMillis();
            
            // 1. ТЯЖЕЛАЯ UNICODE НОРМАЛИЗАЦИЯ (делается каждый раз)
            String normalizedWord = heavyUnicodeNormalization(russianWord);
            
            // 2. ЧТЕНИЕ ВСЕГО ФАЙЛА ПРИ КАЖДОМ ЗАПРОСЕ (вместо кэширования)
            Map<String, String> dictionary = readEntireDictionaryEveryTime();
            
            // 3. СЛОЖНЫЙ REGEX ПОИСК (с lookaround - медленные операции)
            String translation = complexRegexSearch(normalizedWord, dictionary);
            
            // 4. ДОПОЛНИТЕЛЬНЫЕ НЕНУЖНЫЕ ОПЕРАЦИИ
            translation = unnecessaryPostProcessing(translation);
            
            long endTime = System.currentTimeMillis();
            log.info("END translation for {}: {} (took {} ms)", 
                russianWord, translation, endTime - startTime);
            
            return translation;
        }).subscribeOn(Schedulers.boundedElastic()); // Выполняем в отдельном потоке
    }
    
    /**
     * Пакетный перевод слов
     * НЕОПТИМАЛЬНОСТИ:
     * 1. Обработка каждого слова отдельно (нет группировки)
     * 2. Многократная сортировка
     * 3. Дублирующие преобразования
     */
    public Flux<String> translateBatch(List<String> words) {
        log.info("Starting batch translation of {} words", words.size());
        
        return Flux.fromIterable(words)
            // НЕОПТИМАЛЬНО: flatMap создает много потоков
            .flatMap(word -> translate(word))
            .collectList()
            .flatMapMany(translations -> {
                // НЕОПТИМАЛЬНО: Создаем временный список
                List<String> tempList = new ArrayList<>(translations);
                
                // НЕОПТИМАЛЬНО: Сортируем (хотя порядок не важен)
                Collections.sort(tempList);
                
                // НЕОПТИМАЛЬНО: Еще раз сортируем в обратном порядке
                tempList.sort(Collections.reverseOrder());
                
                // НЕОПТИМАЛЬНО: Убираем дубликаты (хотя их не должно быть)
                Set<String> uniqueSet = new HashSet<>(tempList);
                List<String> uniqueList = new ArrayList<>(uniqueSet);
                
                // НЕОПТИМАЛЬНО: Снова сортируем
                Collections.sort(uniqueList);
                
                return Flux.fromIterable(uniqueList);
            })
            .map(String::toUpperCase)  // НЕОПТИМАЛЬНО: Преобразование
            .map(String::toLowerCase)  // НЕОПТИМАЛЬНО: И обратное преобразование
            .distinct()                // НЕОПТИМАЛЬНО: Опять убираем дубликаты
            .sort();                   // НЕОПТИМАЛЬНО: Снова сортируем
    }
    
    // ============================
    // НЕОПТИМАЛЬНЫЕ МЕТОДЫ
    // ============================
    
    /**
     * Тяжелая Unicode нормализация (NFKC форма)
     * Это дорогая операция, которая выполняется каждый раз
     */
    private String heavyUnicodeNormalization(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Нормализация в форму NFKC (самая тяжелая)
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        
        // Удаляем диакритические знаки (ненужно для русского)
        normalized = normalized.replaceAll("\\p{M}", "");
        
        // Приводим к нижнему регистру
        normalized = normalized.toLowerCase();
        
        // Убираем пробелы
        normalized = normalized.trim();
        
        // НЕОПТИМАЛЬНО: Делаем еще раз то же самое
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFKC);
        normalized = normalized.toLowerCase();
        normalized = normalized.trim();
        
        return normalized;
    }
    
    /**
     * Чтение всего файла словаря каждый раз
     * Вместо того чтобы закешировать словарь в памяти
     */
    private Map<String, String> readEntireDictionaryEveryTime() {
        Map<String, String> dictionary = new HashMap<>();
        
        try {
            // Чтение файла с диска при каждом запросе
            ClassPathResource resource = new ClassPathResource("dictionary.txt");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), "UTF-8")
            );
            
            String line;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null) {
                lineCount++;
                
                // Пропускаем пустые строки и комментарии
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // НЕОПТИМАЛЬНО: Применяем тяжелую нормализацию к каждой строке
                line = heavyUnicodeNormalization(line);
                
                // Разделяем по знаку равенства
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String russian = heavyUnicodeNormalization(parts[0]); // Дублирование
                    String english = heavyUnicodeNormalization(parts[1]); // Дублирование
                    
                    // НЕОПТИМАЛЬНО: Проверяем, нет ли уже такого ключа
                    if (dictionary.containsKey(russian)) {
                        // НЕОПТИМАЛЬНО: Логируем конфликт (медленно)
                        log.warn("Duplicate key found: {}", russian);
                    }
                    
                    dictionary.put(russian, english);
                }
            }
            
            reader.close();
            log.info("Dictionary loaded: {} lines, {} entries", lineCount, dictionary.size());
            
            // НЕОПТИМАЛЬНО: Создаем временную копию мапы (бесполезно)
            Map<String, String> tempMap = new HashMap<>(dictionary);
            dictionary.clear();
            dictionary.putAll(tempMap);
            
        } catch (Exception e) {
            log.error("Error reading dictionary file", e);
        }
        
        return dictionary;
    }
    
    /**
     * Поиск перевода с использованием сложных regex
     * Использует lookaround выражения, которые очень медленные
     */
    private String complexRegexSearch(String word, Map<String, String> dictionary) {
        // НЕОПТИМАЛЬНО: Создаем сложный regex с lookaround
        // (?<=^| ) - positive lookbehind (начало строки или пробел)
        // (?= |$) - positive lookahead (пробел или конец строки)
        String regex = "(?<=^|\\s)" + Pattern.quote(word) + "(?=\\s|$)";
        Pattern pattern = Pattern.compile(regex);
        
        String translation = null;
        
        // НЕОПТИМАЛЬНО: Итерируем по всей мапе (O(n) вместо O(1))
        for (Map.Entry<String, String> entry : dictionary.entrySet()) {
            String key = entry.getKey();
            
            // Проверяем точное совпадение с lookaround
            Matcher matcher = pattern.matcher(key);
            if (matcher.find()) {
                translation = entry.getValue();
                break;
            }
            
            // НЕОПТИМАЛЬНО: Дополнительная проверка с другим regex
            Pattern additionalPattern = Pattern.compile(".*" + word + ".*");
            Matcher additionalMatcher = additionalPattern.matcher(key);
            if (additionalMatcher.matches()) {
                translation = entry.getValue();
                // НЕОПТИМАЛЬНО: Не выходим, продолжаем искать
            }
        }
        
        // НЕОПТИМАЛЬНО: Если не нашли, ищем через stream (еще медленнее)
        if (translation == null) {
            translation = dictionary.entrySet().stream()
                .filter(e -> e.getKey().contains(word))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("Translation not found");
        }
        
        return translation;
    }
    
    /**
     * Ненужная постобработка результата
     * ИСПРАВЛЕНО: убрана сортировка букв, которая ломала слова
     */
    private String unnecessaryPostProcessing(String input) {
        if (input == null || input.equals("Translation not found")) {
            return input;
        }
        
        // НЕОПТИМАЛЬНО: Разбиваем строку на массив символов
        char[] chars = input.toCharArray();
        
        // НЕОПТИМАЛЬНО: Создаем список символов
        List<Character> charList = new ArrayList<>();
        for (char c : chars) {
            charList.add(c);
        }
        
        // ИСПРАВЛЕНО: Убрана сортировка букв, которая ломала слова
        // Collections.sort(charList);  ← ЭТУ СТРОКУ УДАЛИЛИ
        
        // НЕОПТИМАЛЬНО: Собираем обратно в строку
        StringBuilder sb = new StringBuilder();
        for (Character c : charList) {
            sb.append(c);
        }
        String result = sb.toString();
        
        // НЕОПТИМАЛЬНО: Еще раз нормализуем
        result = heavyUnicodeNormalization(result);
        
        // НЕОПТИМАЛЬНО: Добавляем префикс и суффикс
        result = "translated: " + result + " :end";
        
        return result;
    }
}