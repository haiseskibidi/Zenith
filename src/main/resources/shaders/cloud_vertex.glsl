#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 2) in vec3 aNormal;
layout(location = 3) in vec3 aInstancePos;             // Позиция из VBO инстансов
layout(location = 4) in vec3 aInstanceScaleSeedAlpha;  // Масштаб, сид и альфа из VBO инстансов

#include "include/global_data.glsl"

out vec3 vNormal;
out vec3 vWorldPos;
out float vAlpha;

// Fast hash for 1D float
float hash11(float p) {
    p = fract(p * .1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

void main() {
    int subIdx = gl_InstanceID % 4;

    vec3 centerPos = aInstancePos;
    float cloudScale = aInstanceScaleSeedAlpha.x;
    float cloudSeed = aInstanceScaleSeedAlpha.y;
    float cloudAlpha = aInstanceScaleSeedAlpha.z;

    // 1. Уникальные псевдослучайные значения для каждого из 4 сегментов облака на основе его сида
    float h1 = hash11(cloudSeed * 17.13 + float(subIdx) * 31.45);
    float h2 = hash11(cloudSeed * 53.71 + float(subIdx) * 73.19);
    float h3 = hash11(cloudSeed * 91.29 + float(subIdx) * 113.87);

    // 2. Случайный масштаб для подчиненных сфер
    float scale = cloudScale;
    vec3 offset = vec3(0.0);
    
    if (subIdx > 0) {
        // Уникальное пространственное смещение подчиненных сфер (кластеризация)
        offset = vec3(
            (h1 - 0.5) * 3.4 * scale,  // Смещение по горизонтали X
            (h2 - 0.5) * 0.75 * scale, // Небольшое кучевое смещение по высоте Y
            (h3 - 0.5) * 2.8 * scale   // Смещение по глубине Z
        );
        scale *= (0.45 + h2 * 0.65);   // Подчиненные сферы имеют разный размер
    } else {
        scale *= 0.95; // Основная центральная сфера чуть больше
    }

    // 3. 3D деформация и сжатие для горизонтальной вытянутости и плоской подошвы
    vec3 stretch = vec3(
        1.4 + h1 * 1.1,      // Разная длина горизонтального растяжения
        0.32 + h2 * 0.15,    // Сплющивание по высоте (кучевая плоская база)
        0.85 + h3 * 0.65     // Разная глубина/ширина
    );

    vec3 localPos = aPos * scale * stretch;

    // Плоская подошва (конденсационный слой):
    if (localPos.y < 0.0) {
        localPos.y *= 0.22; // Сильно сплющиваем нижнюю часть для реалистичности
    }

    vec3 worldPos = centerPos + offset + localPos;

    // Smooth procedural wind swaying based on global time and stable cloud seed
    float swayTime = uTime * 0.4 + cloudSeed * 100.0;
    worldPos.x += sin(swayTime) * 0.25;
    worldPos.z += cos(swayTime * 0.8) * 0.25;

    vNormal = aNormal;
    vWorldPos = worldPos;
    vAlpha = cloudAlpha;

    // Use globally synchronized projection and view matrices from GlobalData UBO!
    gl_Position = gProjection * gView * vec4(worldPos, 1.0);
}
