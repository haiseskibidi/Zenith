<template>
  <div class="simulator-container">
    <div class="sim-header">
      <h4 class="sim-title">Интерактивный симулятор пружины 2-го порядка</h4>
      <p class="sim-desc">
        Поводи мышкой по серой области. Зеленая точка — это твоя мышь (взгляд камеры), а красный меч/круг — это рука игрока, которая летит к цели по законам пружинной физики.
      </p>
    </div>

    <div class="sim-layout">
      <!-- Настройки -->
      <div class="sim-controls">
        <div class="control-group">
          <div class="control-label">
            <span>Масса / Вес (m): <b>{{ mass.toFixed(1) }}</b></span>
            <span class="desc-micro">Инерция предмета</span>
          </div>
          <input 
            type="range" 
            min="0.1" 
            max="8.0" 
            step="0.1" 
            v-model.number="mass" 
            class="range-slider"
          />
        </div>

        <div class="control-group">
          <div class="control-label">
            <span>Жесткость пружины (k): <b>{{ stiffness.toFixed(0) }}</b></span>
            <span class="desc-micro">Сила притяжения к взгляду</span>
          </div>
          <input 
            type="range" 
            min="10" 
            max="250" 
            step="5" 
            v-model.number="stiffness" 
            class="range-slider"
          />
        </div>

        <div class="control-group">
          <div class="control-label">
            <span>Демпфирование / Трение (c): <b>{{ damping.toFixed(1) }}</b></span>
            <span class="desc-micro">Гашение колебаний</span>
          </div>
          <input 
            type="range" 
            min="0.5" 
            max="30" 
            step="0.5" 
            v-model.number="damping" 
            class="range-slider"
          />
        </div>

        <div class="preset-box">
          <span class="preset-title">Пресеты предметов:</span>
          <div class="preset-buttons">
            <button @click="applyPreset(1.0, 50.0, 8.0)" class="preset-btn">Топор (Дерево)</button>
            <button @click="applyPreset(3.5, 30.0, 5.0)" class="preset-btn">Наковальня (Тяжелая)</button>
            <button @click="applyPreset(0.5, 120.0, 18.0)" class="preset-btn">Кинжал (Легкий)</button>
          </div>
        </div>
      </div>

      <!-- Визуализация -->
      <div class="sim-viewport">
        <canvas 
          ref="simCanvas" 
          @mousemove="onMouseMove"
          @mouseleave="onMouseLeave"
          class="canvas-view"
        ></canvas>
        <span class="canvas-tip">Перемещай курсор внутри этой области</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SpringSimulator',
  data() {
    return {
      mass: 1.0,
      stiffness: 50.0,
      damping: 8.0,
      
      // Физическое состояние
      posX: 0,
      posY: 0,
      velX: 0,
      velY: 0,
      
      // Цель (курсор мыши)
      targetX: 0,
      targetY: 0,
      isMouseOver: false,
      
      // Системные переменные для отрисовки
      animationFrameId: null,
      lastTime: 0
    };
  },
  mounted() {
    const canvas = this.$refs.simCanvas;
    // Настраиваем физический размер canvas
    canvas.width = canvas.clientWidth;
    canvas.height = canvas.clientHeight;
    
    // Инициализируем позиции в центр
    this.posX = canvas.width / 2;
    this.posY = canvas.height / 2;
    this.targetX = this.posX;
    this.targetY = this.posY;
    
    this.lastTime = performance.now();
    this.tick();
    
    window.addEventListener('resize', this.onResize);
  },
  beforeUnmount() {
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
    }
    window.removeEventListener('resize', this.onResize);
  },
  methods: {
    applyPreset(m, k, c) {
      this.mass = m;
      this.stiffness = k;
      this.damping = c;
    },
    onMouseMove(e) {
      const rect = this.$refs.simCanvas.getBoundingClientRect();
      this.targetX = e.clientX - rect.left;
      this.targetY = e.clientY - rect.top;
      this.isMouseOver = true;
    },
    onMouseLeave() {
      this.isMouseOver = false;
      const canvas = this.$refs.simCanvas;
      this.targetX = canvas.width / 2;
      this.targetY = canvas.height / 2;
    },
    onResize() {
      const canvas = this.$refs.simCanvas;
      if (!canvas) return;
      canvas.width = canvas.clientWidth;
      canvas.height = canvas.clientHeight;
    },
    tick() {
      const now = performance.now();
      // Вычисляем dt в секундах
      let dt = (now - this.lastTime) / 1000;
      this.lastTime = now;

      // Зажимаем dt, чтобы физика не взрывалась при провисании кадров
      dt = Math.min(dt, 0.05);

      this.updatePhysics(dt);
      this.draw();

      this.animationFrameId = requestAnimationFrame(this.tick);
    },
    updatePhysics(dt) {
      // 1. Сила пружины по X и Y (Закон Гука: F_spring = -k * dx)
      const springForceX = (this.targetX - this.posX) * this.stiffness;
      const springForceY = (this.targetY - this.posY) * this.stiffness;

      // 2. Сила демпфирования (трения) (F_damping = -c * v)
      const dampingForceX = -this.velX * this.damping;
      const dampingForceY = -this.velY * this.damping;

      // 3. Вычисление ускорения (a = F / m)
      const accelX = (springForceX + dampingForceX) / this.mass;
      const accelY = (springForceY + dampingForceY) / this.mass;

      // 4. Интегрирование скорости и координаты (Semi-implicit Euler)
      this.velX += accelX * dt;
      this.velY += accelY * dt;

      this.posX += this.velX * dt;
      this.posY += this.velY * dt;

      // Защита от NaN
      if (!Number.isFinite(this.posX)) this.posX = this.targetX;
      if (!Number.isFinite(this.posY)) this.posY = this.targetY;
    },
    draw() {
      const canvas = this.$refs.simCanvas;
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      
      // Очистка
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      
      // Рисуем сетку
      ctx.strokeStyle = '#22252e';
      ctx.lineWidth = 1;
      const gridSpacing = 40;
      for (let x = 0; x < canvas.width; x += gridSpacing) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, canvas.height);
        ctx.stroke();
      }
      for (let y = 0; y < canvas.height; y += gridSpacing) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(canvas.width, y);
        ctx.stroke();
      }

      // Рисуем виртуальную нить-пружину
      ctx.strokeStyle = '#384252';
      ctx.lineWidth = 2;
      ctx.setLineDash([5, 5]);
      ctx.beginPath();
      ctx.moveTo(this.posX, this.posY);
      ctx.lineTo(this.targetX, this.targetY);
      ctx.stroke();
      ctx.setLineDash([]); // Сброс штриховки

      // Рисуем целевую точку (курсор мыши)
      if (this.isMouseOver) {
        ctx.fillStyle = '#10b981';
        ctx.beginPath();
        ctx.arc(this.targetX, this.targetY, 6, 0, Math.PI * 2);
        ctx.fill();
        ctx.strokeStyle = 'rgba(16, 185, 129, 0.3)';
        ctx.lineWidth = 4;
        ctx.stroke();
      }

      // Рисуем виртуальный объект (кисть руки / меч)
      ctx.fillStyle = '#ef4444';
      ctx.beginPath();
      ctx.arc(this.posX, this.posY, 14, 0, Math.PI * 2);
      ctx.fill();
      
      // Белый зрачок в центре, показывающий инерционный наклон/взгляд
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      // Рассчитываем смещение зрачка на основе текущей скорости
      const maxSpeed = 300;
      const offsetLimit = 6;
      const vxRatio = Math.min(Math.max(this.velX / maxSpeed, -1), 1);
      const vyRatio = Math.min(Math.max(this.velY / maxSpeed, -1), 1);
      ctx.arc(
        this.posX + vxRatio * offsetLimit, 
        this.posY + vyRatio * offsetLimit, 
        4, 0, Math.PI * 2
      );
      ctx.fill();

      // Отрисовка текстовой информации о скоростях
      ctx.fillStyle = '#798299';
      ctx.font = '11px "JetBrains Mono", monospace';
      ctx.fillText(`Target: (${this.targetX.toFixed(0)}, ${this.targetY.toFixed(0)})`, 15, 25);
      ctx.fillText(`Object: (${this.posX.toFixed(0)}, ${this.posY.toFixed(0)})`, 15, 45);
      const speed = Math.sqrt(this.velX * this.velX + this.velY * this.velY);
      ctx.fillText(`Velocity: ${speed.toFixed(0)} px/s`, 15, 65);
    }
  }
};
</script>

<style scoped>
.simulator-container {
  background-color: #16171d;
  border: 1px solid #22252e;
  border-radius: 8px;
  padding: 20px;
  margin: 24px 0;
}

.sim-header {
  margin-bottom: 20px;
}

.sim-title {
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 6px 0;
}

.sim-desc {
  color: #798299;
  font-size: 13px;
  line-height: 1.4;
  margin: 0;
}

.sim-layout {
  display: flex;
  gap: 24px;
}

@media (max-width: 768px) {
  .sim-layout {
    flex-direction: column;
  }
}

.sim-controls {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 240px;
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.control-label {
  display: flex;
  flex-direction: column;
  color: #c9ccd6;
  font-size: 13px;
}

.control-label b {
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
  transition: transform 0.1s ease;
}

.range-slider::-webkit-slider-thumb:hover {
  transform: scale(1.2);
}

.preset-box {
  border-top: 1px solid #22252e;
  padding-top: 16px;
  margin-top: 8px;
}

.preset-title {
  color: #c9ccd6;
  font-size: 12px;
  font-weight: 500;
  display: block;
  margin-bottom: 10px;
}

.preset-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preset-btn {
  background-color: #1b1c24;
  border: 1px solid #282c37;
  border-radius: 4px;
  color: #a0a6b5;
  padding: 8px 12px;
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.preset-btn:hover {
  border-color: #3b82f6;
  color: #ffffff;
  background-color: #1e2230;
}

.sim-viewport {
  position: relative;
  flex: 2;
  height: 320px;
  background-color: #0b0c10;
  border: 1px solid #22252e;
  border-radius: 6px;
  overflow: hidden;
}

.canvas-view {
  width: 100%;
  height: 100%;
  display: block;
  cursor: crosshair;
}

.canvas-tip {
  position: absolute;
  bottom: 12px;
  right: 12px;
  background-color: rgba(11, 12, 16, 0.7);
  color: #555d70;
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 4px;
  pointer-events: none;
  border: 1px solid rgba(34, 37, 46, 0.5);
}
</style>
