#version 330 core

#include "include/global_data.glsl"

in vec3 vNormal;
in vec3 vWorldPos;
in float vAlpha;

uniform float uRainIntensity; // Интенсивность дождя [0.0..1.0]

out vec4 fragColor;

void main() {
    vec3 normal = normalize(vNormal);
    vec3 viewDir = normalize(uCameraPos - vWorldPos);
    vec3 sunDir = -normalize(uSunDirection);

    // 1. Плавное полутоновое освещение Ламберта
    float diff = dot(normal, sunDir);
    float light = smoothstep(-0.3, 0.7, diff);

    // 2. Реалистичные цвета для дня с учетом дождя (Storm transition)
    // В ясную погоду: белоснежные облака с нежной голубоватой тенью.
    // В дождь: тяжелые, свинцово-черные грозовые тучи.
    vec3 shadowColor = mix(vec3(0.68, 0.73, 0.83), vec3(0.10, 0.12, 0.18), uRainIntensity);
    vec3 litColor = mix(vec3(1.0, 1.0, 1.0), vec3(0.20, 0.24, 0.30), uRainIntensity);
    
    // При дожде прямое солнце скрывается, свет становится полностью рассеянным (ambient).
    // Подавляем прямой солнечный свет на 85%, убирая неестественное свечение белых боков туч.
    float lightFactor = mix(light, light * 0.15, uRainIntensity);
    vec3 baseColor = mix(shadowColor, litColor, lightFactor);

    // В дождь принудительно перекрашиваем облака в тяжелый грозовой свинцовый цвет
    // для абсолютной гарантии отсутствия белых/светлых облаков во время шторма.
    if (uRainIntensity > 0.0) {
        vec3 stormColor = vec3(0.12, 0.14, 0.19); // Базовый темно-свинцовый цвет тучи
        baseColor = mix(baseColor, stormColor, uRainIntensity * 0.95);
    }

    // 3. Эффект "серебристой каймы" (Silver Lining)
    // При дожде кайма полностью угасает, так как солнце скрывается за пеленой туч
    float viewDotSun = max(0.0, dot(viewDir, sunDir));
    float edgeDecline = 1.0 - max(0.0, dot(normal, viewDir));
    float silverLining = pow(viewDotSun, 5.0) * pow(edgeDecline, 2.0) * mix(1.5, 0.0, uRainIntensity);
    
    vec3 sunGlowColor = vec3(1.0, 0.93, 0.82);
    baseColor = mix(baseColor, sunGlowColor, silverLining * 0.7 * (1.0 - uRainIntensity));

    // 4. Мягкость и пушистость (Fresnel Transparency) с учетом плотности дождя
    float fresnelAlpha = pow(max(0.0, dot(normal, viewDir)), 0.65);
    
    // В дождь облака становятся гораздо более плотными, густыми и непрозрачными
    float targetMaxAlpha = mix(0.88, 0.98, uRainIntensity);
    float alpha = targetMaxAlpha * fresnelAlpha * vAlpha;

    // 5. Ночное время суток: перекрашиваем под холодную лунную ночь
    if (uIsNight) {
        // Мягкий сине-индиговый лунный свет (в дождь становится совсем темным)
        vec3 nightLit = mix(vec3(0.28, 0.35, 0.52), vec3(0.05, 0.06, 0.10), uRainIntensity);
        baseColor = mix(vec3(0.02, 0.03, 0.06), nightLit, lightFactor);
        
        // В дождь тучи не должны терять плотность ночью, поэтому ослабление убирается
        float nightAlphaMult = mix(0.65, 0.95, uRainIntensity);
        alpha *= nightAlphaMult;
    }

    fragColor = vec4(baseColor, alpha);
}
