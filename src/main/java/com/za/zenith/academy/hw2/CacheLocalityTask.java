package com.za.zenith.academy.hw2;

/**
 * <h1>Домашнее задание 2: Железо — Задача на Cache Locality (Локальность данных в кэше)</h1>
 * 
 * <p><b>Суть проблемы:</b>
 * Память считывается процессором из ОЗУ не побайтово, а блоками — <b>кэш-линиями (Cache Lines)</b>
 * размером обычно 64 байта.
 * В Java двумерный массив {@code long[N][N]} — это массив ссылок на отдельные одномерные массивы {@code long[N]}.
 * Каждый одномерный массив лежит в памяти непрерывно.
 * - <b>Row-Major (Обход по строкам)</b>: Мы читаем элементы {@code matrix[i][j]} последовательно:
 *   {@code [i][0], [i][1], [i][2]}... При чтении первого элемента вся кэш-линия (8 элементов типа long по 8 байт = 64 байта)
 *   автоматически считывается в быстрый L1/L2 кэш CPU. Последующие 7 обращений происходят мгновенно (Cache Hit).
 * - <b>Column-Major (Обход по столбцам)</b>: Мы читаем элементы {@code matrix[j][i]}:
 *   {@code [0][i], [1][i], [2][i]}... Каждое обращение дергает новый одномерный массив, что приводит
 *   к постоянным промахам кэша (Cache Miss) и медленным запросам в основную оперативную память.</p>
 * 
 * <p><b>Студенческое задание:</b>
 * 1. Изучите методы обхода матрицы {@code runRowMajor()} и {@code runColumnMajor()}.
 * 2. Замерьте время выполнения обхода матрицы размером 4000x4000.
 * 3. Объясните колоссальную разницу в скорости с точки зрения архитектуры кэша CPU.</p>
 */
public class CacheLocalityTask {

    private static final int MATRIX_SIZE = 4000;

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println(" Zenith Academy: HW 2. Тест Cache Locality (Cache Lines)");
        System.out.println("=========================================================");

        // Инициализируем матрицу 4000x4000 (около 128 МБ памяти)
        long[][] matrix = new long[MATRIX_SIZE][MATRIX_SIZE];
        for (int i = 0; i < MATRIX_SIZE; i++) {
            for (int j = 0; j < MATRIX_SIZE; j++) {
                matrix[i][j] = i + j;
            }
        }

        // Разогрев JVM
        System.out.println("Разогрев JVM...");
        runRowMajor(matrix);
        runColumnMajor(matrix);

        // Основной тест
        System.out.println("\nЗапуск основного теста...");
        
        long startRow = System.nanoTime();
        long sumRow = runRowMajor(matrix);
        long endRow = System.nanoTime();
        double rowMs = (endRow - startRow) / 1_000_000.0;

        long startCol = System.nanoTime();
        long sumCol = runColumnMajor(matrix);
        long endCol = System.nanoTime();
        double colMs = (endCol - startCol) / 1_000_000.0;

        System.out.printf("Обход по СТРОКАМ (Row-Major):   %10.2f ms (Сумма: %d)\n", rowMs, sumRow);
        System.out.printf("Обход по СТОЛБЦАМ (Column-Major): %10.2f ms (Сумма: %d)\n", colMs, sumCol);
        double speedup = colMs / rowMs;
        System.out.printf("Ускорение за счет Cache Hits:     %.2fx\n", speedup);

        System.out.println("\n[АНАЛИЗ]:");
        System.out.println("- При обходе по строкам мы считываем последовательные 8-байтовые long.");
        System.out.println("  Благодаря кэш-линиям по 64 байта, 7 из 8 обращений попадают в сверхбыстрый L1 кэш.");
        System.out.println("- При обходе по столбцам каждое чтение прыгает по памяти в другой массив,");
        System.out.println("  что гарантирует Cache Miss и заставляет CPU ждать данные из ОЗУ.");
        System.out.println("=========================================================");
    }

    // Обход по строкам (Row-Major): последовательное чтение из памяти
    private static long runRowMajor(long[][] matrix) {
        long sum = 0;
        for (int i = 0; i < MATRIX_SIZE; i++) {
            for (int j = 0; j < MATRIX_SIZE; j++) {
                sum += matrix[i][j]; // matrix[i][j] идет последовательно по строке
            }
        }
        return sum;
    }

    // Обход по столбцам (Column-Major): прыжки по памяти
    private static long runColumnMajor(long[][] matrix) {
        long sum = 0;
        for (int j = 0; j < MATRIX_SIZE; j++) {
            for (int i = 0; i < MATRIX_SIZE; i++) {
                sum += matrix[i][j]; // Прыгаем на MATRIX_SIZE * 8 байт вперед на каждом шаге!
            }
        }
        return sum;
    }
}