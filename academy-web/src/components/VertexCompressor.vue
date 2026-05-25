<template>
  <div class="compressor-container">
    <div class="comp-header">
      <h4 class="comp-title">Интерактивный сжиматель воксельных вершин</h4>
      <p class="comp-desc">
        Изменяй параметры воксельной вершины с помощью ползунков ниже. Справа отобразится битовая сетка двух 32-битных регистров (aPackedData0 и aPackedData1) и GLSL-код распаковки в рантайме.
      </p>
    </div>

    <div class="comp-layout">
      <!-- Ползунки -->
      <div class="comp-sliders">
        <h5 class="sliders-title">Свойства вершины:</h5>
        
        <div class="slider-group">
          <div class="slider-label">
            <span>Координата X (в чанке): <b>{{ posX.toFixed(3) }}</b></span>
            <span class="desc-micro">Точность: 1/16 блока ({{ Math.round(posX * 16) }} / 256)</span>
          </div>
          <input type="range" min="0" max="15.9375" step="0.0625" v-model.number="posX" class="range-slider" />
        </div>

        <div class="slider-group">
          <div class="slider-label">
            <span>Координата Y (высота): <b>{{ posY }}</b></span>
            <span class="desc-micro">Диапазон: 0 - 255</span>
          </div>
          <input type="range" min="0" max="255" step="1" v-model.number="posY" class="range-slider" />
        </div>

        <div class="slider-group">
          <div class="slider-label">
            <span>Координата Z: <b>{{ posZ.toFixed(3) }}</b></span>
            <span class="desc-micro">Точность: 1/16 блока ({{ Math.round(posZ * 16) }} / 256)</span>
          </div>
          <input type="range" min="0" max="15.9375" step="0.0625" v-model.number="posZ" class="range-slider" />
        </div>

        <div class="slider-group">
          <div class="slider-label">
            <span>Нормаль грани (Face Normal):</span>
          </div>
          <select v-model.number="normalIndex" class="comp-select">
            <option :value="0">ВВЕРХ (0, 1, 0)</option>
            <option :value="1">ВНИЗ (0, -1, 0)</option>
            <option :value="2">ВПРАВО (1, 0, 0)</option>
            <option :value="3">ВЛЕВО (-1, 0, 0)</option>
            <option :value="4">ВПЕРЕД (0, 0, 1)</option>
            <option :value="5">НАЗАД (0, 0, -1)</option>
          </select>
        </div>

        <div class="slider-group">
          <div class="slider-label">
            <span>Текстурный слой: <b>{{ textureLayer }}</b></span>
            <span class="desc-micro">Индекс в Texture 2D Array (0 - 63)</span>
          </div>
          <input type="range" min="0" max="63" step="1" v-model.number="textureLayer" class="range-slider" />
        </div>
      </div>

      <!-- Битовое представление и Код -->
      <div class="comp-visuals">
        <h5 class="visuals-title">Битовые регистры GPU (uint32):</h5>
        
        <!-- Регистр 0 -->
        <div class="register-box">
          <div class="reg-title">
            <span>aPackedData0</span>
            <span class="reg-hex">HEX: 0x{{ packed0.toString(16).toUpperCase().padStart(8, '0') }}</span>
          </div>
          <div class="bit-grid">
            <div 
              v-for="bitIdx in 32" 
              :key="bitIdx"
              :class="[
                'bit-cell', 
                { 'bit-active': getBit(packed0, 32 - bitIdx) },
                getBitGroupClass0(32 - bitIdx)
              ]"
              :title="`Бит ${32 - bitIdx}: ${getBitGroupLabel0(32 - bitIdx)}`"
            >
              {{ getBit(packed0, 32 - bitIdx) }}
            </div>
          </div>
          <!-- Легенда Регистра 0 -->
          <div class="reg-legend">
            <span class="leg-item leg-x">X (0-9)</span>
            <span class="leg-item leg-y">Y (10-19)</span>
            <span class="leg-item leg-z">Z (20-29)</span>
            <span class="leg-item leg-empty">Empty (30-31)</span>
          </div>
        </div>

        <!-- Регистр 1 -->
        <div class="register-box">
          <div class="reg-title">
            <span>aPackedData1</span>
            <span class="reg-hex">HEX: 0x{{ packed1.toString(16).toUpperCase().padStart(8, '0') }}</span>
          </div>
          <div class="bit-grid">
            <div 
              v-for="bitIdx in 32" 
              :key="bitIdx"
              :class="[
                'bit-cell', 
                { 'bit-active': getBit(packed1, 32 - bitIdx) },
                getBitGroupClass1(32 - bitIdx)
              ]"
              :title="`Бит ${32 - bitIdx}: ${getBitGroupLabel1(32 - bitIdx)}`"
            >
              {{ getBit(packed1, 32 - bitIdx) }}
            </div>
          </div>
          <!-- Легенда Регистра 1 -->
          <div class="reg-legend">
            <span class="leg-item leg-layer">Layer (0-5)</span>
            <span class="leg-item leg-normal">Normal (6-11)</span>
            <span class="leg-item leg-empty">Empty (12-31)</span>
          </div>
        </div>

        <!-- GLSL Распаковка -->
        <div class="glsl-box">
          <div class="glsl-header">GLSL код распаковки:</div>
          <pre class="glsl-code"><code>// vertex.glsl
layout(location = 0) in uint aPackedData0;
layout(location = 1) in uint aPackedData1;

void main() {
    // Распаковка X, Y, Z
    float x = float(aPackedData0 & 0x3FFu) / 16.0;   // -> {{ posX.toFixed(4) }}
    float y = float((aPackedData0 >> 10) & 0x3FFu) / 16.0; // -> {{ posY.toFixed(4) }}
    float z = float((aPackedData0 >> 20) & 0x3FFu) / 16.0; // -> {{ posZ.toFixed(4) }}
    vec3 position = vec3(x, y, z);

    // Декодирование нормали куба
    uint normalIdx = (aPackedData1 >> 6) & 0x3Fu; // -> Index: {{ normalIndex }}
    vec3 normal = NORMALS[normalIdx];            // -> ({{ getNormalVector() }})

    // Слой текстурного массива
    float layer = float(aPackedData1 & 0x3Fu);    // -> Index: {{ textureLayer }}
}</code></pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'VertexCompressor',
  data() {
    return {
      posX: 2.375,
      posY: 64,
      posZ: 14.125,
      normalIndex: 0,
      textureLayer: 12
    };
  },
  computed: {
    packed0() {
      // Масштабируем координаты в целые числа с шагом 1/16
      const xInt = Math.round(this.posX * 16) & 0x3FF; // 10 бит
      const yInt = Math.round(this.posY * 16) & 0x3FF; // 10 бит
      const zInt = Math.round(this.posZ * 16) & 0x3FF; // 10 бит
      
      // Упаковываем: X (0-9), Y (10-19), Z (20-29)
      return (xInt) | (yInt << 10) | (zInt << 20);
    },
    packed1() {
      const layerVal = this.textureLayer & 0x3F; // 6 бит (0-5)
      const normalVal = this.normalIndex & 0x3F; // 6 бит (6-11)
      
      // Упаковываем: Layer (0-5), Normal (6-11)
      return (layerVal) | (normalVal << 6);
    }
  },
  methods: {
    getBit(number, bitPosition) {
      return (number >> bitPosition) & 1;
    },
    getBitGroupClass0(bit) {
      if (bit >= 0 && bit <= 9) return 'group-x';
      if (bit >= 10 && bit <= 19) return 'group-y';
      if (bit >= 20 && bit <= 29) return 'group-z';
      return 'group-empty';
    },
    getBitGroupLabel0(bit) {
      if (bit >= 0 && bit <= 9) return 'Координата X (Бит ' + bit + ')';
      if (bit >= 10 && bit <= 19) return 'Координата Y (Бит ' + bit + ')';
      if (bit >= 20 && bit <= 29) return 'Координата Z (Бит ' + bit + ')';
      return 'Пусто (Бит ' + bit + ')';
    },
    getBitGroupClass1(bit) {
      if (bit >= 0 && bit <= 5) return 'group-layer';
      if (bit >= 6 && bit <= 11) return 'group-normal';
      return 'group-empty';
    },
    getBitGroupLabel1(bit) {
      if (bit >= 0 && bit <= 5) return 'Текстурный слой (Бит ' + bit + ')';
      if (bit >= 6 && bit <= 11) return 'Индекс нормали (Бит ' + bit + ')';
      return 'Пусто (Бит ' + bit + ')';
    },
    getNormalVector() {
      const normals = [
        "0.0, 1.0, 0.0",
        "0.0, -1.0, 0.0",
        "1.0, 0.0, 0.0",
        "-1.0, 0.0, 0.0",
        "0.0, 0.0, 1.0",
        "0.0, 0.0, -1.0"
      ];
      return normals[this.normalIndex] || "0.0, 0.0, 0.0";
    }
  }
};
</script>

<style scoped>
.compressor-container {
  background-color: #16171d;
  border: 1px solid #22252e;
  border-radius: 8px;
  padding: 20px;
  margin: 24px 0;
}

.comp-header {
  margin-bottom: 20px;
}

.comp-title {
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 6px 0;
}

.comp-desc {
  color: #798299;
  font-size: 13px;
  line-height: 1.4;
  margin: 0;
}

.comp-layout {
  display: flex;
  gap: 24px;
}

@media (max-width: 992px) {
  .comp-layout {
    flex-direction: column;
  }
}

.comp-sliders {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 240px;
}

.sliders-title, .visuals-title {
  color: #ffffff;
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 8px 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.slider-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.slider-label {
  display: flex;
  flex-direction: column;
  color: #c9ccd6;
  font-size: 13px;
}

.slider-label b {
  color: #3b82f6;
}

.desc-micro {
  color: #555d70;
  font-size: 11px;
  margin-top: 2px;
}

.range-slider {
  -webkit-appearance: none;
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: #282c37;
  outline: none;
  cursor: pointer;
}

.range-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #3b82f6;
  cursor: pointer;
}

.comp-select {
  background-color: #1b1c24;
  border: 1px solid #282c37;
  border-radius: 4px;
  color: #c9ccd6;
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
  cursor: pointer;
  width: 100%;
}

.comp-select:focus {
  border-color: #3b82f6;
}

.comp-visuals {
  flex: 1.5;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.register-box {
  background-color: #0b0c10;
  border: 1px solid #22252e;
  border-radius: 6px;
  padding: 16px;
}

.reg-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #ffffff;
  font-size: 13px;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 500;
  margin-bottom: 12px;
}

.reg-hex {
  color: #10b981;
}

.bit-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  margin-bottom: 12px;
}

.bit-cell {
  width: calc(100% / 16 - 3px);
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-family: 'JetBrains Mono', monospace;
  border-radius: 2px;
  background-color: #1b1c24;
  color: #4b5269;
  user-select: none;
  cursor: help;
  transition: all 0.1s ease;
}

@media (max-width: 480px) {
  .bit-cell {
    width: calc(100% / 8 - 3px);
  }
}

.bit-active {
  color: #ffffff !important;
  font-weight: bold;
}

/* Групповая раскраска битов для Регистра 0 */
.group-x.bit-active { background-color: rgba(59, 130, 246, 0.8); } /* Синий X */
.group-x { border: 1px solid rgba(59, 130, 246, 0.3); }

.group-y.bit-active { background-color: rgba(16, 185, 129, 0.8); } /* Зеленый Y */
.group-y { border: 1px solid rgba(16, 185, 129, 0.3); }

.group-z.bit-active { background-color: rgba(168, 85, 247, 0.8); } /* Фиолетовый Z */
.group-z { border: 1px solid rgba(168, 85, 247, 0.3); }

/* Групповая раскраска битов для Регистра 1 */
.group-layer.bit-active { background-color: rgba(245, 158, 11, 0.8); } /* Оранжевый Layer */
.group-layer { border: 1px solid rgba(245, 158, 11, 0.3); }

.group-normal.bit-active { background-color: rgba(236, 72, 153, 0.8); } /* Розовый Normal */
.group-normal { border: 1px solid rgba(236, 72, 153, 0.3); }

.group-empty {
  border: 1px solid #1b1c24;
  opacity: 0.3;
}

.reg-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 11px;
}

.leg-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #a0a6b5;
}

.leg-item::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}

.leg-x::before { background-color: #3b82f6; }
.leg-y::before { background-color: #10b981; }
.leg-z::before { background-color: #a855f7; }
.leg-layer::before { background-color: #f59e0b; }
.leg-normal::before { background-color: #ec7299; }
.leg-empty::before { background-color: #282c37; }

.glsl-box {
  background-color: #0b0c10;
  border: 1px solid #22252e;
  border-radius: 6px;
  padding: 16px;
  overflow: hidden;
}

.glsl-header {
  color: #ffffff;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 10px;
}

.glsl-code {
  margin: 0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #c9ccd6;
  white-space: pre-wrap;
}

.glsl-code code {
  font-family: inherit;
}
</style>
