package com.za.zenith.academy.hw1;

import java.util.Arrays;
import java.util.Random;

/**
 * <h1>Домашнее задание 1: Железо — Задача на Branch Prediction (Предсказатель переходов CPU)</h1>
 * 
 * <p><b>Суть проблемы:</b>
 * Современные процессоры суперскалярны и используют конвейеры (pipelines) для одновременного выполнения
 * нескольких инструкций на разных стадиях. Встречая условный переход (например, {@code if (data[i] > 128)}),
 * процессор не знает, какое направление будет выбрано, пока условие не вычислится полностью.
 * Чтобы не простаивать, <b>Branch Predictor</b> (предсказатель переходов) пытается угадать направление
 * и CPU начинает спекулятивно выполнять инструкции по выбранной ветви.
 * - Если угадал: конвейер полон, выполнение мгновенно.
 * - Если не угадал (Branch Misprediction): CPU вынужден полностью сбросить конвейер, откатить результаты
 *   спекулятивных вычислений и загрузить верные инструкции заново. Это стоит 15-20 тактов процессора.</p>
 * 
 * <p><b>Студенческое задание:</b>
 * 1. Изучите метод {@code runFilter(int[] data)}, содержащий условное ветвление в цикле.
 * 2. Замерьте время выполнения на полностью случайном (неотсортированном) массиве.
 * 3. Замерьте время выполнения на отсортированном массиве (где сначала идут все числа < 128, а затем >= 128).
 * 4. Оцените огромную разницу в производительности и изучите лог-анализ.</p>
 */
public class BranchPredictionTask {

    private static final int ARRAY_SIZE = 15_000_000;

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println(" Zenith Academy: HW 1. Тест Branch Prediction");
        System.out.println("=========================================================");

        // Заполняем массив случайными числами от 0 до 255
        int[] unsortedData = new int[ARRAY_SIZE];
        Random rand = new Random(2026);
        for (int i = 0; i < ARRAY_SIZE; i++) {
            unsortedData[i] = rand.nextInt(256);
        }

        // Создаем копию для сортировки
        int[] sortedData = Arrays.copyOf(unsortedData, unsortedData.length);
        
        System.out.println("Сортировка массива (это время НЕ входит в замер фильтрации)...");
        Arrays.sort(sortedData);

        // Разогрев JVM
        System.out.println("Разогрев JVM...");
        runFilter(unsortedData);
        runFilter(sortedData);

        // Основной тест
        System.out.println("\nЗапуск основного теста...");
        
        long startUnsorted = System.nanoTime();
        long sumUnsorted = runFilter(unsortedData);
        long endUnsorted = System.nanoTime();
        double unsortedMs = (endUnsorted - startUnsorted) / 1_000_000.0;

        long startSorted = System.nanoTime();
        long sumSorted = runFilter(sortedData);
        long endSorted = System.nanoTime();
        double sortedMs = (endSorted - startSorted) / 1_000_000.0;

        System.out.printf("Неотсортированный массив (Хаос):   %10.2f ms (Сумма: %d)\n", unsortedMs, sumUnsorted);
        System.out.printf("Отсортированный массив (Порядок):  %10.2f ms (Сумма: %d)\n", sortedMs, sumSorted);
        double speedup = unsortedMs / sortedMs;
        System.out.printf("Ускорение за счет предсказателя:   %.2fx\n", speedup);

        System.out.println("\n[АНАЛИЗ]:");
        System.out.println("- На неотсортированном массиве Branch Predictor ошибается примерно в 50% случаев.");
        System.out.println("  Постоянные сбросы конвейера CPU вызывают сильные задержки.");
        System.out.println("- На отсортированном массиве данные упорядочены (все ложные условия идут первыми,");
        System.out.println("  затем все истинные). Предсказатель переходов быстро понимает паттерн и ошибается");
        System.out.println("  ровно ОДИН раз (на границе перехода от <128 к >=128).");
        System.out.println("=========================================================");
    }

    private static long runFilter(int[] data) {
        long sum = 0;
        for (int i = 0; i < data.length; i++) {
            // Ветвление, критичное для Branch Predictor
            if (data[i] >= 128) {
                sum += data[i];
            }
        }
        return sum;
    }
}