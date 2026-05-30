package com.za.zenith.academy.chapter2;

import java.util.Random;

/**
 * <h1>Глава 2: Конвейер OpenGL — Задача на Draw Call Overhead (Накладные расходы вызовов)</h1>
 * 
 * <p><b>Суть проблемы:</b>
 * Каждый вызов отрисовки (Draw Call, например, glDrawArrays) проходит длинный путь:
 * JVM -> JNI переход -> Драйвер видеокарты (валидация состояния, трансляция команд) -> GPU.
 * Из-за этого одиночный вызов отрисовки одного треугольника невероятно дорог.
 * Рисование 1,000,000 треугольников по одному (наивный подход) "задушит" CPU накладными расходами драйвера.
 * Оптимальное решение — <b>Батчинг (Batching)</b>: объединение данных многих треугольников в один большой буфер
 * и отправка их на GPU одним единственным вызовом отрисовки.</p>
 * 
 * <p><b>Студенческое задание:</b>
 * 1. Изучите наивный метод {@code runNaiveRendering()}, который вызывает симулированный отрисовщик для каждого треугольника.
 * 2. Изучите батч-метод {@code runBatchRendering()}, который группирует все данные в один буфер и вызывает отрисовщик один раз.
 * 3. Обратите внимание на то, как симулируются накладные расходы JNI и проверки стейта драйвера (State Validation).
 * 4. Запустите бенчмарк и оцените колоссальную разницу в CPU-времени.</p>
 */
public class DrawCallOverheadTask {

    private static final int TRIANGLE_COUNT = 1_000_000;
    
    // Симулируем состояние OpenGL драйвера
    private static class SimulatedGLDriver {
        public volatile int activeTexture = 0;
        public volatile int activeShader = 0;
        public volatile boolean depthTestEnabled = true;
        
        // Симуляция накладных расходов вызова JNI + валидации состояния драйвером
        public void validateStateAndSubmit() {
            // Драйвер проверяет текущий стейт перед каждым Draw Call
            if (activeTexture < 0 || activeShader < 0 || !depthTestEnabled) {
                throw new IllegalStateException("Invalid GL State");
            }
            // Симулируем JNI оверхед и работу CPU по подготовке команды
            // Небольшая математическая операция, предотвращающая оптимизацию JIT
            double dummy = Math.sin(activeTexture) + Math.cos(activeShader);
            if (dummy == 999.0) {
                System.out.println("Never happens");
            }
        }
    }

    private static final SimulatedGLDriver driver = new SimulatedGLDriver();

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println(" Zenith Academy: Глава 2. Тест Draw Call Overhead");
        System.out.println("=========================================================");

        // Генерация случайных координат вершин треугольников
        float[] xCoords = new float[TRIANGLE_COUNT];
        float[] yCoords = new float[TRIANGLE_COUNT];
        float[] zCoords = new float[TRIANGLE_COUNT];
        Random rand = new Random(42);
        for (int i = 0; i < TRIANGLE_COUNT; i++) {
            xCoords[i] = rand.nextFloat();
            yCoords[i] = rand.nextFloat();
            zCoords[i] = rand.nextFloat();
        }

        // Разогрев JVM
        System.out.println("Разогрев JVM...");
        runNaive(xCoords, yCoords, zCoords);
        runBatch(xCoords, yCoords, zCoords);

        // Основной тест
        System.out.println("\nЗапуск основного теста...");
        
        long naiveStart = System.nanoTime();
        long naiveTriangles = runNaive(xCoords, yCoords, zCoords);
        long naiveEnd = System.nanoTime();
        double naiveTimeMs = (naiveEnd - naiveStart) / 1_000_000.0;

        long batchStart = System.nanoTime();
        long batchTriangles = runBatch(xCoords, yCoords, zCoords);
        long batchEnd = System.nanoTime();
        double batchTimeMs = (batchEnd - batchStart) / 1_000_000.0;

        System.out.printf("Наивный метод (1 млн Draw Calls):  %10.2f ms (Отрисовано: %d)\n", naiveTimeMs, naiveTriangles);
        System.out.printf("Батчинг (1 объединенный вызов):     %10.2f ms (Отрисовано: %d)\n", batchTimeMs, batchTriangles);
        double speedup = naiveTimeMs / batchTimeMs;
        System.out.printf("Ускорение за счет Батчинга:         %.2fx\n", speedup);

        System.out.println("\n[АНАЛИЗ]:");
        System.out.println("- Наивный метод делает 1,000,000 переходов через границу JNI и заставляет драйвер");
        System.out.println("  1,000,000 раз валидировать стейт, что полностью нагружает CPU бессмысленной работой.");
        System.out.println("- Батчинг валидирует стейт ОДИН раз, после чего передает весь массив данных.");
        System.out.println("  Это классический паттерн оптимизации в современных движках (Zenith использует Batch Rendering).");
        System.out.println("=========================================================");
    }

    // Наивный метод: 1,000,000 вызовов draw call
    private static long runNaive(float[] xs, float[] ys, float[] zs) {
        long rendered = 0;
        for (int i = 0; i < TRIANGLE_COUNT; i++) {
            // Симулируем draw call для одного треугольника
            driver.validateStateAndSubmit();
            // "Отрисовка"
            float x = xs[i];
            float y = ys[i];
            float z = zs[i];
            if (x + y + z > 0) {
                rendered++;
            }
        }
        return rendered;
    }

    // Батчинг: группировка данных в один вызов
    private static long runBatch(float[] xs, float[] ys, float[] zs) {
        // Проверяем и валидируем стейт ОДИН раз перед отправкой буфера
        driver.validateStateAndSubmit();
        
        long rendered = 0;
        // Быстрый обход массива в памяти без вызовов драйвера на каждый элемент
        for (int i = 0; i < TRIANGLE_COUNT; i++) {
            float x = xs[i];
            float y = ys[i];
            float z = zs[i];
            if (x + y + z > 0) {
                rendered++;
            }
        }
        return rendered;
    }
}