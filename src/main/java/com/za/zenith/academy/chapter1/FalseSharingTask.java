package com.za.zenith.academy.chapter1;

/**
 * <h1>Глава 1: Память и JVM — Задача на False Sharing (Ложное разделение кэша)</h1>
 * 
 * <p><b>Суть проблемы:</b>
 * Процессоры обмениваются данными с памятью через кэш-линии (обычно размером 64 байта).
 * Когда поток на одном ядре CPU модифицирует переменную, вся кэш-линия, содержащая эту переменную,
 * помечается как недействительная (invalid) во всех остальных ядрах (протоколы когерентности кэшей, например MESI).
 * Если две независимые volatile переменные попадают в одну кэш-линию, потоки, пишущие в них с разных ядер,
 * будут постоянно инвалидировать кэш друг друга, вызывая чудовищные накладные расходы на синхронизацию
 * через системную шину (Cache Ping-Pong).</p>
 * 
 * <p><b>Студенческое задание:</b>
 * 1. Изучите класс {@link BadSharing}, в котором две переменные {@code value1} и {@code value2}
 *    лежат рядом без какого-либо разделения.
 * 2. Добавьте кэш-паддинг (8 полей типа long: p1, p2, ..., p7) между volatile полями в классе {@link PaddedSharing},
 *    чтобы гарантировать, что они окажутся в разных кэш-линиях (long занимает 8 байт, 7 * 8 = 56 байт + служебные данные
 *    гарантированно разносят поля).
 * 3. Изучите комментарии о том, как аннотация {@code @jdk.internal.vm.annotation.Contended} (или {@code @Contended})
 *    решает эту задачу на уровне JVM автоматически (требует запуска с флагом {@code -XX:-RestrictContended}).
 * </p>
 */
public class FalseSharingTask {

    private static final long ITERATIONS = 150_000_000L;

    // --- 1. НЕЭФФЕКТИВНЫЙ ВАРИАНТ (False Sharing) ---
    public static class BadSharing {
        public volatile long value1 = 0L;
        // Две volatile переменные лежат вплотную в памяти объекта.
        // Скорее всего, они попадут в одну кэш-линию 64 байта.
        public volatile long value2 = 0L;
    }

    // --- 2. ЭФФЕКТИВНЫЙ ВАРИАНТ (С ручным кэш-паддингом) ---
    public static class PaddedSharing {
        public volatile long value1 = 0L;
        
        // ==========================================
        // ЗАДАЧА СТУДЕНТА: Добавить кэш-паддинг.
        // 7 полей long (7 * 8 = 56 байт) гарантируют,
        // что value1 и value2 окажутся в разных кэш-линиях (размер линии = 64 байта).
        // ==========================================
        public long p1, p2, p3, p4, p5, p6, p7;
        
        public volatile long value2 = 0L;
    }

    /*
     * ПРИМЕЧАНИЕ ДЛЯ СТУДЕНТА:
     * В современной Java (начиная с Java 8) существует стандартный способ борьбы с False Sharing:
     * аннотация @jdk.internal.vm.annotation.Contended (или просто @Contended в зависимости от версии JDK).
     * Если пометить ей поле или класс, JVM автоматически добавит необходимый паддинг.
     * 
     * Пример:
     * 
     * public static class ContendedSharing {
     *     @jdk.internal.vm.annotation.Contended
     *     public volatile long value1 = 0L;
     *     
     *     public volatile long value2 = 0L;
     * }
     * 
     * ВАЖНО: Чтобы эта аннотация работала для пользовательских классов, JVM должна быть
     * запущена с флагом: -XX:-RestrictContended (по умолчанию он включен и разрешает паддинг только для классов JDK).
     */

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=========================================================");
        System.out.println(" Zenith Academy: Глава 1. Тест производительности False Sharing");
        System.out.println("=========================================================");
        
        // Разогрев JVM
        System.out.println("Разогрев JVM...");
        runBenchmark(new BadSharing(), new PaddedSharing(), true);
        
        // Основной тест
        System.out.println("\nЗапуск основного теста...");
        runBenchmark(new BadSharing(), new PaddedSharing(), false);
        
        System.out.println("=========================================================");
    }

    private static void runBenchmark(BadSharing bad, PaddedSharing padded, boolean warmup) throws InterruptedException {
        long badTime = runBadSharingTest(bad);
        long paddedTime = runPaddedSharingTest(padded);

        if (!warmup) {
            System.out.printf("Без паддинга (False Sharing):  %10d ms\n", badTime);
            System.out.printf("С ручным паддингом (Оптимум):  %10d ms\n", paddedTime);
            double speedup = (double) badTime / paddedTime;
            System.out.printf("Ускорение за счет паддинга:    %.2fx\n", speedup);
            
            System.out.println("\n[АНАЛИЗ]:");
            System.out.println("- Без паддинга два ядра процессора постоянно борются за одну кэш-линию.");
            System.out.println("  Каждая запись одного ядра инвалидирует L1/L2 кэш второго ядра.");
            System.out.println("- Добавление long полей-заполнителей разносит volatile-переменные в разные");
            System.out.println("  кэш-линии. Каждое ядро пишет в свою кэш-линию, избегая конфликтов на шине.");
        }
    }

    private static long runBadSharingTest(final BadSharing badObj) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            for (long i = 0; i < ITERATIONS; i++) {
                badObj.value1 = i;
            }
        });

        Thread t2 = new Thread(() -> {
            for (long i = 0; i < ITERATIONS; i++) {
                badObj.value2 = i;
            }
        });

        long start = System.currentTimeMillis();
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        return System.currentTimeMillis() - start;
    }

    private static long runPaddedSharingTest(final PaddedSharing paddedObj) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            for (long i = 0; i < ITERATIONS; i++) {
                paddedObj.value1 = i;
            }
        });

        Thread t2 = new Thread(() -> {
            for (long i = 0; i < ITERATIONS; i++) {
                paddedObj.value2 = i;
            }
        });

        long start = System.currentTimeMillis();
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        return System.currentTimeMillis() - start;
    }
}