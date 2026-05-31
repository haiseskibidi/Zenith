package com.za.zenith.engine.graphics;

import com.za.zenith.world.World;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

public class CloudRenderSystem {
    private Shader shader;
    private int vaoId = -1;
    private int vboId = -1;
    private int eboId = -1;
    private int instanceVbo = -1;
    private int indexCount = 0;
    private boolean initialized = false;
    private int frameCounter = 0;

    public void init() {
        if (initialized) return;
        
        // Компилируем шейдеры облаков
        shader = new Shader("src/main/resources/shaders/cloud_vertex.glsl", "src/main/resources/shaders/cloud_fragment.glsl");
        
        // Генерируем и настраиваем геометрию сферы на низком уровне
        createSphereGeometry();
        
        initialized = true;
    }

    private void createSphereGeometry() {
        int rings = 8;
        int sectors = 12;
        float radius = 1.0f;
        
        int vertCount = (rings + 1) * (sectors + 1);
        float[] vertices = new float[vertCount * 6]; // 3 pos, 3 norm
        
        int vIdx = 0;
        for (int r = 0; r <= rings; r++) {
            float theta = r * (float)Math.PI / rings;
            float sinTheta = (float)Math.sin(theta);
            float cosTheta = (float)Math.cos(theta);
            
            for (int s = 0; s <= sectors; s++) {
                float phi = s * 2 * (float)Math.PI / sectors;
                float sinPhi = (float)Math.sin(phi);
                float cosPhi = (float)Math.cos(phi);
                
                float x = cosPhi * sinTheta;
                float y = cosTheta;
                float z = sinPhi * sinTheta;
                
                // Position
                vertices[vIdx] = x * radius;
                vertices[vIdx+1] = y * radius;
                vertices[vIdx+2] = z * radius;
                
                // Normal
                vertices[vIdx+3] = x;
                vertices[vIdx+4] = y;
                vertices[vIdx+5] = z;
                
                vIdx += 6;
            }
        }
        
        List<Integer> indicesList = new ArrayList<>();
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                int cur = r * (sectors + 1) + s;
                int next = cur + sectors + 1;
                
                indicesList.add(cur);
                indicesList.add(next);
                indicesList.add(cur + 1);
                
                indicesList.add(cur + 1);
                indicesList.add(next);
                indicesList.add(next + 1);
            }
        }
        
        indexCount = indicesList.size();
        int[] indices = new int[indexCount];
        for (int i = 0; i < indexCount; i++) {
            indices[i] = indicesList.get(i);
        }
        
        // Создаем VAO
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        
        // Создаем статический VBO для геометрии сферы
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        
        // Настраиваем атрибуты геометрии сферы
        // Локация 0: Позиция (vec3)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        
        // Локация 2: Нормаль (vec3)
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        
        // Создаем EBO для индексов сферы
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        // Создаем динамический VBO для данных инстансов облаков (6 float: X, Y, Z, scale, seed, alpha)
        instanceVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER, 256 * 6 * Float.BYTES, GL_DYNAMIC_DRAW);
        
        // Атрибут 3: Позиция инстанса (location = 3, vec3)
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glVertexAttribDivisor(3, 4); // Шаг 4 сферы на облако
        
        // Атрибут 4: Масштаб, Сид, Альфа (location = 4, vec3)
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(4, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        glVertexAttribDivisor(4, 4); // Шаг 4 сферы на облако
        
        // Разбинживаем
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void render(Camera camera, World world, float alpha) {
        if (!initialized || world == null) return;

        List<World.CloudInstance> clouds = world.getActiveClouds();
        if (clouds.isEmpty()) {
            frameCounter++;
            if (frameCounter % 300 == 0) {
                com.za.zenith.utils.Logger.warn("Clouds render: activeClouds list is empty! No clouds registered.");
            }
            return;
        }

        float windTime = world.getWindTime();

        Vector3f camPos = camera.getPosition();
        List<World.CloudInstance> visibleClouds = new ArrayList<>();
        
        for (World.CloudInstance c : clouds) {
            if (c.isCollected()) continue; // Пропускаем собранные облака

            float vx = c.x + windTime;
            float dx = vx - camPos.x;
            float dz = c.z - camPos.z;
            float distSq = dx * dx + dz * dz;
            if (distSq < 192.0f * 192.0f) {
                visibleClouds.add(c);
            }
        }

        // Сортируем облака по их стабильному уникальному ID.
        visibleClouds.sort((a, b) -> Integer.compare(a.id, b.id));

        int count = Math.min(visibleClouds.size(), 256);
        
        // Логгируем состояние облаков раз в 5 секунд для точной диагностики
        frameCounter++;
        if (frameCounter % 300 == 0) {
            com.za.zenith.utils.Logger.info("Clouds render: total active = %d, visible = %d, count to render = %d", 
                    clouds.size(), visibleClouds.size(), count);
        }

        if (count == 0) return;

        // Упаковываем позиции в плоский float массив (6 float на одно облако: X, Y, Z, scale, seed, alpha)
        float[] positionsData = new float[count * 6];
        for (int i = 0; i < count; i++) {
            World.CloudInstance c = visibleClouds.get(i);
            
            float vx = c.x + windTime;
            float dx = vx - camPos.x;
            float dz = c.z - camPos.z;
            float dist = (float)Math.sqrt(dx * dx + dz * dz);
            
            // Плавно гасим альфу на границах зоны видимости (150-192 метров) для Fog-aware Horizon Fade
            float boundaryAlpha = 1.0f;
            if (dist > 150.0f) {
                boundaryAlpha = Math.max(0.0f, (192.0f - dist) / (192.0f - 150.0f));
            }
            float finalAlpha = c.getAlpha() * boundaryAlpha;

            positionsData[i * 6] = vx;
            positionsData[i * 6 + 1] = c.y;
            positionsData[i * 6 + 2] = c.z;
            positionsData[i * 6 + 3] = c.scale;
            positionsData[i * 6 + 4] = c.seed;
            positionsData[i * 6 + 5] = finalAlpha; // Передаем плавную прозрачность с учетом границ!
        }

        // Сохраняем и настраиваем стейты OpenGL
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false); // Отключаем запись глубины, чтобы облака не затягивались пост-процессинговым туманом!
        glEnable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE); // Выключаем точечный MSAA шум для идеальной гладкости полупрозрачных облаков!

        shader.use();
        shader.setFloat("uRainIntensity", world.getWeatherManager().getRainIntensity());
        
        // Загружаем данные инстансов в VBO перед отрисовкой
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, positionsData);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Рендеринг тел реалистичных облаков (один пасс, CW грани — внешние, отсекаем FRONT/CCW)
        glCullFace(GL_FRONT);
        glBindVertexArray(vaoId);
        glDrawElementsInstanced(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0, count * 4);
        glBindVertexArray(0);

        // Возвращаем стандартные настройки OpenGL
        glDepthMask(true); // Возвращаем запись в буфер глубины
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE); // Возвращаем Alpha-to-Coverage для воксельной травы и листвы
    }

    public void cleanup() {
        if (!initialized) return;
        if (shader != null) shader.cleanup();
        if (vaoId != -1) glDeleteVertexArrays(vaoId);
        if (vboId != -1) glDeleteBuffers(vboId);
        if (eboId != -1) glDeleteBuffers(eboId);
        if (instanceVbo != -1) glDeleteBuffers(instanceVbo);
        initialized = false;
    }
}
