package com.za.zenith.academy.chapter9;

import java.util.Arrays;
import java.util.Random;

/**
 * <h1>Глава 9: РПГ Механики — Задача на Loot Weight (Выборка по весам)</h1>
 * 
 * <p><b>Суть проблемы:</b>
 * При генерации добычи (loot) в RPG у каждого предмета есть свой "вес" (вероятность выпадения).
 * - Наивный поиск за O(N) проходит всю таблицу лута, накапливая сумму весов, пока она не превысит случайное число.
 *   Если таблица большая (сотни или тысячи предметов), а генерация вызывается часто (например, при спавне мобов
 *   или разрушении блоков в мире Zenith), этот алгоритм станет узким местом.
 * - Оптимальный поиск за O(log N) использует предварительно рассчитанные <b>префиксные суммы</b>
 *   и <b>бинарный поиск</b> для мгновенного нахождения нужного предмета.</p>
 * 
 * <p><b>Студенческое задание:</b>
 * 1. Изучите наивный метод {@code selectLootNaive()}, работающий за O(N).
 * 2. Изучите оптимальный метод {@code selectLootBinary()}, использующий префиксные суммы и {@link Arrays#binarySearch(double[], double)} за O(log N).
 * 3. Запустите тест производительности на 2,000,000 генераций и оцените разницу.</p>
 */
public class LootWeightTask {

    private static final int LOOT_ITEMS_COUNT = 1000;
    private static final int GENERATION_COUNT = 2_000_000;

    public record LootItem(String name, double weight) {}

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println(" Zenith Academy: Глава 9. Тест Loot Weight (RPG Loot Table)");
        System.out.println("=========================================================");

        // Инициализируем таблицу лута
        LootItem[] lootTable = new LootItem[LOOT_ITEMS_COUNT];
        double totalWeight = 0.0;
        Random rand = new Random(1337);
        
        for (int i = 0; i < LOOT_ITEMS_COUNT; i++) {
            // Разные веса: обычные предметы имеют большой вес, редкие — маленький
            double weight = rand.nextDouble() * 100.0;
            if (i % 50 == 0) weight = 0.1; // Легендарный лут
            lootTable[i] = new LootItem("item_id_" + i, weight);
            totalWeight += weight;
        }

        // Подготовка префиксных сумм
        double[] prefixSums = new double[LOOT_ITEMS_COUNT];
        double currentSum = 0.0;
        for (int i = 0; i < LOOT_ITEMS_COUNT; i++) {
            currentSum += lootTable[i].weight();
            prefixSums[i] = currentSum;
        }

        // Подготовка случайных чисел для генерации (чтобы тесты были абсолютно идентичными)
        double[] targets = new double[GENERATION_COUNT];
        for (int i = 0; i < GENERATION_COUNT; i++) {
            targets[i] = rand.nextDouble() * totalWeight;
        }

        // Разогрев JVM
        System.out.println("Разогрев JVM...");
        runNaiveBenchmark(lootTable, targets);
        runBinaryBenchmark(prefixSums, targets);

        // Основной тест
        System.out.println("\nЗапуск основного теста (2,000,000 генераций)...");
        
        long naiveStart = System.nanoTime();
        long naiveSumIdx = runNaiveBenchmark(lootTable, targets);
        long naiveEnd = System.nanoTime();
        double naiveMs = (naiveEnd - naiveStart) / 1_000_000.0;

        long binaryStart = System.nanoTime();
        long binarySumIdx = runBinaryBenchmark(prefixSums, targets);
        long binaryEnd = System.nanoTime();
        double binaryMs = (binaryEnd - binaryStart) / 1_000_000.0;

        System.out.printf("Наивный метод O(N):          %10.2f ms (Контрольная сумма: %d)\n", naiveMs, naiveSumIdx);
        System.out.printf("Бинарный поиск O(log N):     %10.2f ms (Контрольная сумма: %d)\n", binaryMs, binarySumIdx);
        double speedup = naiveMs / binaryMs;
        System.out.printf("Ускорение алгоритма O(log N): %.2fx\n", speedup);

        System.out.println("\n[АНАЛИЗ]:");
        System.out.println("- При O(N) в худшем случае нам приходится делать 1000 итераций на каждый предмет.");
        System.out.println("- При O(log N) с помощью бинарного поиска мы находим предмет максимум за log2(1000) ≈ 10 сравнений.");
        System.out.println("  Это позволяет безболезненно масштабировать таблицы лута до десятков тысяч предметов.");
        System.out.println("=========================================================");
    }

    private static long runNaiveBenchmark(LootItem[] table, double[] targets) {
        long checksum = 0;
        for (double target : targets) {
            int selectedIdx = selectLootNaive(table, target);
            checksum += selectedIdx;
        }
        return checksum;
    }

    private static long runBinaryBenchmark(double[] prefixSums, double[] targets) {
        long checksum = 0;
        for (double target : targets) {
            int selectedIdx = selectLootBinary(prefixSums, target);
            checksum += selectedIdx;
        }
        return checksum;
    }

    // Наивный поиск за O(N)
    private static int selectLootNaive(LootItem[] table, double targetWeight) {
        double accumulated = 0.0;
        for (int i = 0; i < table.length; i++) {
            accumulated += table[i].weight();
            if (targetWeight <= accumulated) {
                return i;
            }
        }
        return table.length - 1;
    }

    // Оптимальный поиск за O(log N) через префиксные суммы
    private static int selectLootBinary(double[] prefixSums, double targetWeight) {
        int idx = Arrays.binarySearch(prefixSums, targetWeight);
        if (idx < 0) {
            idx = -idx - 1;
        }
        if (idx >= prefixSums.length) {
            idx = prefixSums.length - 1;
        }
        return idx;
    }
}