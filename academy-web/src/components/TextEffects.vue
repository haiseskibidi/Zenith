<template>
  <div class="effects-container">
    <div class="effects-header">
      <h4 class="effects-title">Живой симулятор шейдерных эффектов</h4>
      <p class="effects-desc">
        Редактируй текст ниже. Используй коды форматирования (<b>$z</b> — радуга, <b>$v</b> — волна, <b>$q</b> — тряска, <b>$g</b> — свечение, <b>$r</b> — сбросить стили), чтобы увидеть их живой рендеринг:
      </p>
    </div>

    <!-- Редактор текста -->
    <div class="editor-input-box">
      <input 
        type="text" 
        v-model="inputText" 
        class="effects-input" 
        placeholder="Введи текст с кодами форматирования..."
      />
    </div>

    <!-- Результат -->
    <div class="effects-output-box">
      <div class="output-header">Финальный рендеринг:</div>
      <div class="effects-output">
        <span 
          v-for="(char, idx) in parsedCharacters" 
          :key="idx"
          :class="[
            'char-span', 
            char.colorClass,
            {
              'effect-rainbow': char.effects.rainbow,
              'effect-wave': char.effects.wave,
              'effect-shake': char.effects.shake,
              'effect-glow': char.effects.glow
            }
          ]"
          :style="{
            '--char-index': idx,
            '--shake-x': char.shakeOffset.x + 'px',
            '--shake-y': char.shakeOffset.y + 'px'
          }"
        >{{ char.char }}</span>
      </div>
    </div>

    <!-- Таблица кодов -->
    <div class="codes-table-box">
      <span class="table-title">Доступные коды форматирования:</span>
      <div class="codes-grid">
        <div class="code-card" @click="appendCode('$z')">
          <span class="code-val">$z</span>
          <span class="code-name effect-rainbow-static">Rainbow (Радуга)</span>
        </div>
        <div class="code-card" @click="appendCode('$v')">
          <span class="code-val">$v</span>
          <span class="code-name">Wave (Волна)</span>
        </div>
        <div class="code-card" @click="appendCode('$q')">
          <span class="code-val">$q</span>
          <span class="code-name">Shake (Тряска)</span>
        </div>
        <div class="code-card" @click="appendCode('$g')">
          <span class="code-val">$g</span>
          <span class="code-name effect-glow-static">Glow (Свечение)</span>
        </div>
        <div class="code-card" @click="appendCode('$r')">
          <span class="code-val">$r</span>
          <span class="code-name">Сброс стилей</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TextEffects',
  data() {
    return {
      inputText: '$zZenith $vEngine $qSystem $gVFX',
      parsedCharacters: [],
      shakeInterval: null
    };
  },
  watch: {
    inputText: {
      handler: 'parseText',
      immediate: true
    }
  },
  mounted() {
    // Интервал для обновления тряски ($q) в реальном времени
    this.shakeInterval = setInterval(() => {
      this.parsedCharacters.forEach(char => {
        if (char.effects.shake) {
          char.shakeOffset = {
            x: (Math.random() - 0.5) * 2.5,
            y: (Math.random() - 0.5) * 2.5
          };
        } else {
          char.shakeOffset = { x: 0, y: 0 };
        }
      });
    }, 40);
  },
  beforeUnmount() {
    if (this.shakeInterval) {
      clearInterval(this.shakeInterval);
    }
  },
  methods: {
    appendCode(code) {
      this.inputText += code;
    },
    parseText() {
      const chars = [];
      let currentEffects = {
        rainbow: false,
        wave: false,
        shake: false,
        glow: false
      };
      let currentColorClass = '';
      
      const text = this.inputText;
      let i = 0;
      
      while (i < text.length) {
        if (text[i] === '$' && i + 1 < text.length) {
          const code = text[i + 1].toLowerCase();
          
          if (code === 'z') {
            currentEffects.rainbow = true;
          } else if (code === 'v') {
            currentEffects.wave = true;
          } else if (code === 'q') {
            currentEffects.shake = true;
          } else if (code === 'g') {
            currentEffects.glow = true;
          } else if (code === 'r') {
            // Сброс всех эффектов
            currentEffects = { rainbow: false, wave: false, shake: false, glow: false };
            currentColorClass = '';
          } else {
            // Проверяем цвета $0-9, $a-f
            const isHex = /[0-9a-f]/.test(code);
            if (isHex) {
              currentColorClass = `color-code-${code}`;
            }
          }
          
          i += 2; // Пропускаем сам код '$x'
        } else {
          chars.push({
            char: text[i],
            effects: { ...currentEffects },
            colorClass: currentColorClass,
            shakeOffset: { x: 0, y: 0 }
          });
          i++;
        }
      }
      
      this.parsedCharacters = chars;
    }
  }
};
</script>

<style scoped>
.effects-container {
  background-color: #16171d;
  border: 1px solid #22252e;
  border-radius: 8px;
  padding: 20px;
  margin: 24px 0;
}

.effects-header {
  margin-bottom: 20px;
}

.effects-title {
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 6px 0;
}

.effects-desc {
  color: #798299;
  font-size: 13px;
  line-height: 1.4;
  margin: 0;
}

.editor-input-box {
  margin-bottom: 20px;
}

.effects-input {
  width: 100%;
  box-sizing: border-box;
  background-color: #0b0c10;
  border: 1px solid #22252e;
  border-radius: 6px;
  color: #ffffff;
  padding: 12px 16px;
  font-size: 14px;
  font-family: 'JetBrains Mono', monospace;
  outline: none;
  transition: border-color 0.2s ease;
}

.effects-input:focus {
  border-color: #3b82f6;
}

.effects-output-box {
  background-color: #0b0c10;
  border: 1px solid #22252e;
  border-radius: 6px;
  padding: 20px;
  margin-bottom: 24px;
  min-height: 80px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.output-header {
  color: #555d70;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.effects-output {
  display: flex;
  flex-wrap: wrap;
  font-size: 26px;
  font-family: 'JetBrains Mono', monospace;
  font-weight: 700;
  letter-spacing: 1px;
}

.char-span {
  display: inline-block;
  white-space: pre;
  transition: transform 0.05s linear;
}

/* ─── ШЕЙДЕРНЫЕ АНИМАЦИИ НА ЧИСТОМ CSS ─── */

/* 1. Эффект Радуги ($z) */
.effect-rainbow {
  animation: rainbow-anim 4s linear infinite;
  animation-delay: calc(var(--char-index) * -0.15s);
}

@keyframes rainbow-anim {
  0% { color: #ef4444; }
  17% { color: #f59e0b; }
  33% { color: #10b981; }
  50% { color: #06b6d4; }
  67% { color: #3b82f6; }
  83% { color: #8b5cf6; }
  100% { color: #ef4444; }
}

.effect-rainbow-static {
  background: linear-gradient(to right, #ef4444, #f59e0b, #10b981, #3b82f6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* 2. Эффект Волны ($v) */
.effect-wave {
  animation: wave-anim 1.5s ease-in-out infinite;
  animation-delay: calc(var(--char-index) * -0.08s);
}

@keyframes wave-anim {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

/* 3. Эффект Тряски ($q) */
.effect-shake {
  transform: translate(var(--shake-x, 0), var(--shake-y, 0));
}

/* 4. Эффект Свечения ($g) */
.effect-glow {
  animation: glow-anim 2s ease-in-out infinite;
}

@keyframes glow-anim {
  0%, 100% {
    text-shadow: 0 0 2px rgba(255, 255, 255, 0.1);
    opacity: 0.6;
  }
  50% {
    text-shadow: 0 0 10px rgba(59, 130, 246, 0.6), 0 0 20px rgba(59, 130, 246, 0.3);
    opacity: 1;
    color: #ffffff;
  }
}

.effect-glow-static {
  text-shadow: 0 0 8px rgba(59, 130, 246, 0.5);
  color: #ffffff;
}

/* Коды цветов (Палитра Minecraft) */
.color-code-0 { color: #000000; }
.color-code-1 { color: #0000aa; }
.color-code-2 { color: #00aa00; }
.color-code-3 { color: #00aaaa; }
.color-code-4 { color: #aa0000; }
.color-code-5 { color: #aa00aa; }
.color-code-6 { color: #ffaa00; }
.color-code-7 { color: #aaaaaa; }
.color-code-8 { color: #555555; }
.color-code-9 { color: #5555ff; }
.color-code-a { color: #55ff55; }
.color-code-b { color: #55ffff; }
.color-code-c { color: #ff5555; }
.color-code-d { color: #ff55ff; }
.color-code-e { color: #ffff55; }
.color-code-f { color: #ffffff; }

.codes-table-box {
  border-top: 1px solid #22252e;
  padding-top: 16px;
}

.table-title {
  color: #c9ccd6;
  font-size: 13px;
  font-weight: 500;
  display: block;
  margin-bottom: 12px;
}

.codes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}

.code-card {
  background-color: #1b1c24;
  border: 1px solid #282c37;
  border-radius: 4px;
  padding: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.code-card:hover {
  border-color: #3b82f6;
  background-color: #1e2230;
}

.code-val {
  background-color: #0b0c10;
  color: #10b981;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: bold;
}

.code-name {
  color: #a0a6b5;
  font-size: 12px;
  font-weight: 500;
}
</style>
