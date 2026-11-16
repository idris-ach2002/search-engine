package com.sorbonne.backend;

import com.sorbonne.backend.ranking.repository.PageRankRepository.WeightedEdge;
import com.sorbonne.backend.ranking.service.PageRankCalculator;
import com.sorbonne.backend.similarity.repository.SimilarityGraphRepository.PairIntersection;
import com.sorbonne.backend.similarity.service.JaccardGraphBuilder;
import com.sorbonne.backend.similarity.service.SimilarityStopWords;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Benchmark {
    
    static Map<Long, Integer> featureCounts = new HashMap<>();
    static Map<String, List<Long>> invertedIndex = new HashMap<>();
    static Map<String, Integer> globalWordCounts = new HashMap<>();
    static List<String> allBooksText = new ArrayList<>();
    static long maxBookId = 0;

    public static void main(String[] args) throws Exception {
        Path booksDir = Paths.get("../backend/data/books");
        if (!Files.exists(booksDir)) booksDir = Paths.get("backend/data/books");

        SimilarityStopWords stopWords = new SimilarityStopWords();
        Set<String> excluded = stopWords.values();
        Pattern wordPattern = Pattern.compile("[a-z]+");

        // 1. LECTURE ET INDEXATION
        long t0 = System.nanoTime();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(booksDir, "*.txt")) {
            for (Path path : stream) {
                String text = Files.readString(path).toLowerCase();
                allBooksText.add(text);
                Map<String, Integer> termCounts = new HashMap<>();
                var matcher = wordPattern.matcher(text);
                while (matcher.find()) {
                    String word = matcher.group();
                    if (word.length() >= 3 && !excluded.contains(word)) {
                        termCounts.merge(word, 1, Integer::sum);
                        globalWordCounts.merge(word, 1, Integer::sum);
                    }
                }
                
                Set<String> features = new HashSet<>();
                for (var entry : termCounts.entrySet()) {
                    if (entry.getValue() >= 2) {
                        features.add(entry.getKey());
                        invertedIndex.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(maxBookId);
                    }
                }
                featureCounts.put(maxBookId, features.size());
                maxBookId++;
            }
        }
        long t1 = System.nanoTime();
        System.out.printf("Lecture : %d ms (%d livres)%n", (t1 - t0) / 1_000_000, maxBookId);

        // 2. ZIPF
        List<Map.Entry<String, Integer>> sortedWords = globalWordCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(1000)
                .collect(Collectors.toList());
        StringBuilder zipfCsv = new StringBuilder("rang,frequence\n");
        for (int i = 0; i < sortedWords.size(); i++) {
            zipfCsv.append(i + 1).append(",").append(sortedWords.get(i).getValue()).append("\n");
        }
        Files.writeString(Path.of("bench_zipf.csv"), zipfCsv.toString());

        // 3. JACCARD (SCALABILITÉ)
        StringBuilder jaccardCsv = new StringBuilder("livres,temps_ms\n");
        int[] limits = {100, 300, 500, 1000, (int) maxBookId};
        for (int limit : limits) {
            if (limit > maxBookId) continue;
            
            Map<String, Integer> pairCounts = new HashMap<>();
            for (List<Long> ids : invertedIndex.values()) {
                List<Long> filteredIds = ids.stream().filter(id -> id < limit).collect(Collectors.toList());
                for (int i = 0; i < filteredIds.size(); i++) {
                    for (int j = i + 1; j < filteredIds.size(); j++) {
                        long a = Math.min(filteredIds.get(i), filteredIds.get(j));
                        long b = Math.max(filteredIds.get(i), filteredIds.get(j));
                        long key = (a << 32) | b;
                        pairCounts.merge(String.valueOf(key), 1, Integer::sum);
                    }
                }
            }

            List<PairIntersection> intersections = new ArrayList<>();
            for (var entry : pairCounts.entrySet()) {
                if (entry.getValue() >= 2) {
                    long key = Long.parseLong(entry.getKey());
                    long a = key >>> 32;
                    long b = key & 0xFFFFFFFFL;
                    intersections.add(new PairIntersection(a, b, entry.getValue()));
                }
            }

            Map<Long, Integer> fCounts = featureCounts.entrySet().stream()
                    .filter(e -> e.getKey() < limit)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            JaccardGraphBuilder builder = new JaccardGraphBuilder();
            long start = System.nanoTime();
            var result = builder.build(fCounts, intersections, 0.05, 20);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            jaccardCsv.append(limit).append(",").append(elapsed).append("\n");
            System.out.printf("Jaccard %d livres : %d ms (%d arêtes)%n", limit, elapsed, result.edges().size());
            
            // On sauvegarde les arêtes finales pour PageRank (uniquement le dernier run)
            if (limit == maxBookId) {
                List<WeightedEdge> prEdges = result.edges().stream()
                        .map(e -> new WeightedEdge(e.bookAId(), e.bookBId(), e.score()))
                        .collect(Collectors.toList());
                runPageRankBenchmark(prEdges);
            }
        }
        Files.writeString(Path.of("bench_jaccard.csv"), jaccardCsv.toString());

        // 4. KMP VS REGEX
        runSearchBenchmark();
    }

    private static void runPageRankBenchmark(List<WeightedEdge> edges) throws Exception {
        PageRankCalculator calculator = new PageRankCalculator();
        List<Long> nodes = featureCounts.keySet().stream().sorted().collect(Collectors.toList());
        StringBuilder prCsv = new StringBuilder("iteration,delta\n");
        
        long start = System.nanoTime();
        for (int i = 1; i <= 20; i++) {
            var res = calculator.calculate(nodes, edges, 0.85, 1e-10, i);
            prCsv.append(i).append(",").append(res.finalDelta()).append("\n");
            if (res.converged()) break;
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        Files.writeString(Path.of("bench_pagerank.csv"), prCsv.toString());
        System.out.printf("PageRank : convergence en %d ms%n", elapsed);
    }

    private static void runSearchBenchmark() throws Exception {
        String[] kmpQueries = {"man", "adventure", "love", "time", "night"};
        String[] regexQueries = {"m(an|en)", "l[a-z]+e", "t[a-z]+e", "n[a-z]+t", "a[a-z]+d"};
        
        StringBuilder searchCsv = new StringBuilder("methode,requete,temps_ms\n");
        
        List<String> terms = new ArrayList<>(invertedIndex.keySet());
        
        for (String q : kmpQueries) {
            long start = System.nanoTime();
            int matches = 0;
            for (String term : terms) {
                if (kmpSearch(term, q)) matches++;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            searchCsv.append("KMP,").append(q).append(",").append(elapsed).append("\n");
            System.out.printf("KMP [%s] : %d ms (%d termes)%n", q, elapsed, matches);
        }

        for (String q : regexQueries) {
            Pattern p = Pattern.compile(q);
            long start = System.nanoTime();
            int matches = 0;
            for (String term : terms) {
                if (p.matcher(term).find()) matches++;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            searchCsv.append("REGEX,").append(q).append(",").append(elapsed).append("\n");
            System.out.printf("REGEX [%s] : %d ms (%d termes)%n", q, elapsed, matches);
        }
        
        Files.writeString(Path.of("bench_search.csv"), searchCsv.toString());
    }

    private static boolean kmpSearch(String text, String pattern) {
        int[] lps = new int[pattern.length()];
        int len = 0;
        for (int i = 1; i < pattern.length(); ) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                lps[i++] = ++len;
            } else if (len != 0) {
                len = lps[len - 1];
            } else {
                lps[i++] = 0;
            }
        }
        int i = 0, j = 0;
        while (i < text.length()) {
            if (pattern.charAt(j) == text.charAt(i)) {
                i++; j++;
                if (j == pattern.length()) return true;
            } else if (j != 0) {
                j = lps[j - 1];
            } else {
                i++;
            }
        }
        return false;
    }
}