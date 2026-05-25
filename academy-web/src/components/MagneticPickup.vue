<template>
  <div class="magnetic-pickup-box">
    <div class="header-row">
      <div class="title-badge">🔬 Лаборатория Zenith: Lead Pursuit Magnetism</div>
      <p class="subtitle">Интерактивный симулятор магнитного притягивания предметов к игроку с упреждающей физикой траектории (Lead Pursuit).</p>
    </div>

    <div class="sandbox-layout">
      <!-- Контрольная панель -->
      <div class="control-panel">
        <h3>1. Физические Настройки</h3>
        
        <div class="control-group">
          <label class="checkbox-label">
            <input type="checkbox" v-model="magnetEnabled" class="custom-checkbox" />
            🧲 Включить Магнитный Компонент
          </label>
        </div>

        <div class="control-group">
          <label>Радиус притяжения (Attraction Radius): {{ attractionRadius }}px</label>
          <input type="range" min="50" max="300" v-model.number="attractionRadius" class="custom-slider" />
          <span class="subtext">Дистанция, на которой предмет замечает магнит и просыпается.</span>
        </div>

        <div class="control-group">
          <label>Сила притяжения (Attraction Force): {{ attractionForce }}</label>
          <input type="range" min="5" max="35" v-model.number="attractionForce" class="custom-slider" />
          <span class="subtext">Влияет на квадратичное ускорение сближения.</span>
        </div>

        <div class="control-group">
          <label>Режим притяжения:</label>
          <select v-model="pursuitMode" class="custom-select">
            <option value="lead_pursuit">🎯 Lead Pursuit (С упреждением скорости игрока)</option>
            <option value="dumb_chase">🚫 Dumb Chase (Простая погоня в старую точку)</option>
          </select>
          <span class="subtext">Lead Pursuit предотвращает бесконечное кружение по орбите при быстром беге.</span>
        </div>

        <div class="btn-row">
          <button @click="spawnItem" class="spawn-btn">✨ Бросить предмет</button>
          <button @click="clearItems" class="clear-btn">🗑️ Очистить все</button>
        </div>
      </div>

      <!-- Canvas Арена -->
      <div class="visualizer-panel">
        <div class="canvas-container">
          <canvas ref="arenaCanvas" @mousedown="onMouseDown" @mousemove="onMouseMove" @mouseup="onMouseUp"></canvas>
          <div class="legend-box">
            <div class="legend-item"><span class="legend-color player"></span> Игрок (Тащи мышкой!)</div>
            <div class="legend-item"><span class="legend-color radius"></span> Зона притяжения</div>
            <div class="legend-item"><span class="legend-color vector-vel"></span> Вектор скорости игрока</div>
            <div class="legend-item"><span class="legend-color vector-pull"></span> Вектор притяжения лута</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Пояснительная консоль -->
    <div class="math-panel">
      <h3>📈 Физические уравнения под капотом</h3>
      <div class="formula-box">
        <div class="formula-line">
          <strong>Квадратичная скорость:</strong> 
          <code class="code-math">approachSpeed = 12.0 + (1.0 - Min(1.0, distance / 4.0)) * (Force * 0.2)</code>
        </div>
        <div class="formula-line" v-if="pursuitMode === 'lead_pursuit'">
          <strong>Lead Pursuit (С упреждением):</strong> 
          <code class="code-math text-success">Velocity = PlayerVelocity + DirectionToPlayer * approachSpeed</code>
        </div>
        <div class="formula-line" v-else>
          <strong>Dumb Chase (Без упреждения):</strong> 
          <code class="code-math text-danger">Velocity = DirectionToPlayer * approachSpeed</code>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MagneticPickup',
  data() {
    return {
      magnetEnabled: true,
      attractionRadius: 180,
      attractionForce: 15,
      pursuitMode: 'lead_pursuit',
      
      // Физические объекты
      player: {
        x: 350,
        y: 150,
        radius: 20,
        vx: 0,
        vy: 0,
        prevX: 350,
        prevY: 150
      },
      items: [],
      isDraggingPlayer: false,
      animationFrameId: null,
      canvasWidth: 500,
      canvasHeight: 300
    };
  },
  methods: {
    spawnItem() {
      // Спавним предмет со случайными координатами и начальным толчком
      const px = 50 + Math.random() * (this.canvasWidth - 100);
      const py = 50 + Math.random() * (this.canvasHeight - 120);
      
      const icons = ['🔧', '🔨', '🪓', '📦', '🔩', '🔋'];
      const icon = icons[Math.floor(Math.random() * icons.length)];

      this.items.push({
        x: px,
        y: py,
        vx: (Math.random() - 0.5) * 8,
        vy: -5 - Math.random() * 5,
        rotation: Math.random() * Math.PI * 2,
        angularVelocity: (Math.random() - 0.5) * 5,
        icon,
        isBeingAttracted: false,
        isLockedOnPlayer: false,
        isSleeping: false,
        sleepTimer: 0,
        trail: [] // Для красивого хвоста траектории
      });
    },
    clearItems() {
      this.items = [];
    },
    onMouseDown(e) {
      const rect = this.$refs.arenaCanvas.getBoundingClientRect();
      const mx = e.clientX - rect.left;
      const my = e.clientY - rect.top;

      const dx = mx - this.player.x;
      const dy = my - this.player.y;
      const dist = Math.sqrt(dx*dx + dy*dy);

      if (dist < this.player.radius + 10) {
        this.isDraggingPlayer = true;
      }
    },
    onMouseMove(e) {
      if (!this.isDraggingPlayer) return;
      const rect = this.$refs.arenaCanvas.getBoundingClientRect();
      this.player.x = e.clientX - rect.left;
      this.player.y = e.clientY - rect.top;
      
      // Ограничиваем игрока границами Canvas
      this.player.x = Math.max(20, Math.min(this.canvasWidth - 20, this.player.x));
      this.player.y = Math.max(20, Math.min(this.canvasHeight - 20, this.player.y));
    },
    onMouseUp() {
      this.isDraggingPlayer = false;
    },
    updatePhysics() {
      // 1. Рассчитываем скорость игрока
      this.player.vx = (this.player.x - this.player.prevX) * 0.4;
      this.player.vy = (this.player.y - this.player.prevY) * 0.4;
      this.player.prevX = this.player.x;
      this.player.prevY = this.player.y;

      const deltaTime = 0.016; // 60 FPS тик
      const gravity = 12.0;
      const groundY = this.canvasHeight - 40;

      // 2. Рассчитываем физику предметов
      this.items.forEach(item => {
        // Добавляем точку в хвост траектории
        item.trail.push({ x: item.x, y: item.y });
        if (item.trail.length > 15) item.trail.shift();

        if (item.isSleeping) {
          // Спящие предметы лежат на земле и опрашивают магнит игрока
          if (this.magnetEnabled) {
            const dx = this.player.x - item.x;
            const dy = this.player.y - item.y;
            const dist = Math.sqrt(dx*dx + dy*dy);

            if (dist < this.attractionRadius) {
              item.isSleeping = false;
              item.isBeingAttracted = true;
              item.isLockedOnPlayer = true;
            }
          }
          return;
        }

        // Логика притяжения
        if (this.magnetEnabled) {
          const dx = this.player.x - item.x;
          const dy = this.player.y - item.y;
          const distSq = dx*dx + dy*dy;
          const dist = Math.sqrt(distSq);

          // Проверка подбора игроком (влет в карман)
          if (dist < this.player.radius + 12) {
            // Удаляем предмет, он "подобран"
            const idx = this.items.indexOf(item);
            if (idx > -1) this.items.splice(idx, 1);
            return;
          }

          if (dist < this.attractionRadius || item.isLockedOnPlayer) {
            item.isBeingAttracted = true;
            item.isLockedOnPlayer = true;

            const dirX = dx / dist;
            const dirY = dy / dist;

            // Квадратичное увеличение скорости по мере приближения
            // Нормализуем дистанцию: 0 на игроке, 1 на границе притяжения
            const normDist = Math.min(1.0, dist / this.attractionRadius);
            const approachSpeed = 4.0 + (1.0 - normDist) * (this.attractionForce * 0.3);

            if (this.pursuitMode === 'lead_pursuit') {
              // Lead Pursuit: наследуем скорость игрока + притягиваемся
              item.vx = this.player.vx + dirX * approachSpeed;
              item.vy = this.player.vy + dirY * approachSpeed;
            } else {
              // Dumb Chase: летим тупо на игрока
              item.vx = dirX * approachSpeed;
              item.vy = dirY * approachSpeed;
            }

            // Бешеное угловое вращение
            item.angularVelocity += deltaTime * 12.0;
          } else {
            item.isBeingAttracted = false;
            item.isLockedOnPlayer = false;
          }
        } else {
          item.isBeingAttracted = false;
          item.isLockedOnPlayer = false;
        }

        // Физика падения (гравитация), если не притягивается
        if (!item.isBeingAttracted) {
          item.vy += gravity * deltaTime;
          
          // Трение с воздухом
          item.vx *= 0.99;
          item.vy *= 0.99;
        }

        // Смещение предмета
        item.x += item.vx;
        item.y += item.vy;

        // Коллизия с землей (groundY)
        if (item.y > groundY) {
          item.y = groundY;
          item.vy = -item.vy * 0.3; // Отскок
          item.vx *= 0.6; // Трение о землю
          
          if (Math.abs(item.vy) < 0.2 && Math.abs(item.vx) < 0.2) {
            item.vy = 0;
            item.vx = 0;
            item.angularVelocity = 0;
            item.rotation = 0;
            
            if (!item.isBeingAttracted) {
              item.sleepTimer += deltaTime;
              if (item.sleepTimer > 1.0) {
                item.isSleeping = true;
              }
            }
          }
        } else {
          item.sleepTimer = 0;
        }

        // Применяем вращение
        item.rotation += item.angularVelocity * deltaTime;
        item.angularVelocity *= 0.98; // Затухание вращения
      });
    },
    draw() {
      const canvas = this.$refs.arenaCanvas;
      if (!canvas) return;
      const ctx = canvas.getContext('2d');

      // Чистим экран
      ctx.fillStyle = '#11111b';
      ctx.fillRect(0, 0, this.canvasWidth, this.canvasHeight);

      // Рисуем землю
      ctx.strokeStyle = '#313244';
      ctx.lineWidth = 4;
      ctx.beginPath();
      ctx.moveTo(0, this.canvasHeight - 40);
      ctx.lineTo(this.canvasWidth, this.canvasHeight - 40);
      ctx.stroke();

      // Рисуем зону притяжения магнита вокруг игрока
      if (this.magnetEnabled) {
        ctx.strokeStyle = 'rgba(203, 166, 247, 0.15)';
        ctx.fillStyle = 'rgba(203, 166, 247, 0.03)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.arc(this.player.x, this.player.y, this.attractionRadius, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
      }

      // Рисуем предметы
      this.items.forEach(item => {
        // Рисуем след траектории
        ctx.strokeStyle = item.isBeingAttracted ? 'rgba(203, 166, 247, 0.3)' : 'rgba(166, 173, 200, 0.15)';
        ctx.lineWidth = 2;
        ctx.beginPath();
        item.trail.forEach((t, i) => {
          if (i === 0) ctx.moveTo(t.x, t.y);
          else ctx.lineTo(t.x, t.y);
        });
        ctx.stroke();

        ctx.save();
        ctx.translate(item.x, item.y);
        ctx.rotate(item.rotation);
        
        // Отрисовка подложки предмета (согласно редкости)
        ctx.fillStyle = item.isSleeping ? '#313244' : (item.isBeingAttracted ? '#cba6f7' : '#bac2de');
        ctx.beginPath();
        ctx.arc(0, 0, 14, 0, Math.PI * 2);
        ctx.fill();

        // Рисуем иконку
        ctx.fillStyle = '#11111b';
        ctx.font = '14px serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(item.icon, 0, 0);

        ctx.restore();

        // Рисуем вектор сближения лута (физическая визуализация из Главы 10)
        if (item.isBeingAttracted) {
          ctx.strokeStyle = '#fab387'; // Цвет вектора
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.moveTo(item.x, item.y);
          ctx.lineTo(item.x + item.vx * 8, item.y + item.vy * 8);
          ctx.stroke();
        }
      });

      // Рисуем игрока
      ctx.fillStyle = '#1e1e2e';
      ctx.strokeStyle = '#cba6f7';
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(this.player.x, this.player.y, this.player.radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();

      // Магнитик внутри игрока
      ctx.fillStyle = '#fab387';
      ctx.font = '16px serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('🤠', this.player.x, this.player.y);

      // Рисуем вектор скорости игрока
      if (Math.abs(this.player.vx) > 0.5 || Math.abs(this.player.vy) > 0.5) {
        ctx.strokeStyle = '#a6e3a1'; // Зеленый для скорости игрока
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.moveTo(this.player.x, this.player.y);
        ctx.lineTo(this.player.x + this.player.vx * 15, this.player.y + this.player.vy * 15);
        ctx.stroke();
      }
    },
    loop() {
      this.updatePhysics();
      this.draw();
      this.animationFrameId = requestAnimationFrame(this.loop);
    }
  },
  mounted() {
    const canvas = this.$refs.arenaCanvas;
    canvas.width = this.canvasWidth;
    canvas.height = this.canvasHeight;

    // Спавним парочку стартовых предметов
    this.spawnItem();
    this.spawnItem();

    // Запуск игрового цикла
    this.loop();
  },
  beforeDestroy() {
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
    }
  }
};
</script>

<style scoped>
.magnetic-pickup-box {
  background: rgba(30, 30, 46, 0.4);
  border: 1px solid rgba(49, 50, 68, 0.6);
  border-radius: 12px;
  padding: 24px;
  margin: 20px 0;
  backdrop-filter: blur(10px);
}

.header-row {
  margin-bottom: 20px;
}

.title-badge {
  background: linear-gradient(135deg, #cba6f7, #fab387);
  color: #11111b;
  font-weight: 800;
  font-size: 0.9rem;
  padding: 4px 10px;
  border-radius: 6px;
  display: inline-block;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.subtitle {
  color: #a6adc8;
  font-size: 0.95rem;
  margin: 0;
}

.sandbox-layout {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 20px;
  margin-bottom: 20px;
}

.control-panel {
  background: rgba(17, 17, 27, 0.5);
  border: 1px solid rgba(49, 50, 68, 0.4);
  border-radius: 10px;
  padding: 20px;
}

.control-panel h3 {
  margin-top: 0;
  color: #cdd6f4;
  font-size: 1.1rem;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #cdd6f4;
  font-size: 0.9rem;
  cursor: pointer;
  margin-bottom: 12px;
}

.custom-checkbox {
  width: 16px;
  height: 16px;
  accent-color: #cba6f7;
  cursor: pointer;
}

.control-group {
  margin-bottom: 16px;
}

.control-group label {
  display: block;
  color: #bac2de;
  font-size: 0.85rem;
  margin-bottom: 6px;
}

.custom-slider {
  width: 100%;
  accent-color: #cba6f7;
  cursor: pointer;
}

.custom-select {
  width: 100%;
  background: #11111b;
  border: 1px solid #313244;
  border-radius: 6px;
  color: #cdd6f4;
  padding: 8px 12px;
  font-size: 0.9rem;
  outline: none;
  cursor: pointer;
}

.subtext {
  display: block;
  font-size: 0.75rem;
  color: #7f849c;
  margin-top: 4px;
}

.btn-row {
  display: flex;
  gap: 10px;
  margin-top: 15px;
}

.spawn-btn {
  flex: 1.2;
  background: linear-gradient(135deg, #cba6f7, #89b4fa);
  color: #11111b;
  border: none;
  font-weight: bold;
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.2s;
}

.spawn-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.clear-btn {
  flex: 0.8;
  background: transparent;
  border: 1px solid #f38ba8;
  color: #f38ba8;
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: background 0.2s;
}

.clear-btn:hover {
  background: rgba(243, 139, 168, 0.1);
}

.visualizer-panel {
  background: rgba(17, 17, 27, 0.3);
  border: 1px solid rgba(49, 50, 68, 0.3);
  border-radius: 10px;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 10px;
}

.canvas-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

canvas {
  border: 1px solid rgba(49, 50, 68, 0.5);
  border-radius: 8px;
  cursor: crosshair;
  background: #11111b;
  width: 100%;
  max-width: 500px;
}

.legend-box {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  justify-content: center;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #a6adc8;
  font-size: 0.75rem;
}

.legend-color {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.legend-color.player { background: #cba6f7; }
.legend-color.radius { background: rgba(203, 166, 247, 0.2); border: 1px solid #cba6f7; }
.legend-color.vector-vel { background: #a6e3a1; }
.legend-color.vector-pull { background: #fab387; }

/* Формулы */
.math-panel {
  background: #11111b;
  border: 1px solid #313244;
  border-radius: 10px;
  padding: 16px;
}

.math-panel h3 {
  margin-top: 0;
  margin-bottom: 12px;
  color: #f9e2af;
  font-size: 0.95rem;
}

.formula-box {
  background: #010102;
  border-radius: 6px;
  padding: 12px;
  border: 1px solid rgba(49, 50, 68, 0.5);
}

.formula-line {
  margin-bottom: 8px;
  font-size: 0.85rem;
  color: #bac2de;
}

.formula-line:last-child {
  margin-bottom: 0;
}

.code-math {
  font-family: 'Fira Code', monospace;
  font-size: 0.8rem;
  background: rgba(49, 50, 68, 0.3);
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 8px;
}

.text-success { color: #a6e3a1; }
.text-danger { color: #f38ba8; }
</style>
