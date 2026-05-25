<template>
  <div class="loot-generator-box">
    <div class="header-row">
      <div class="title-badge">🔬 Лаборатория Zenith: RPG & Loot Engine</div>
      <p class="subtitle">Интерактивный симулятор генерации лута, весов распределения и процедурного согласования русских родов.</p>
    </div>

    <div class="sandbox-layout">
      <!-- Контрольная панель -->
      <div class="control-panel">
        <h3>1. Настройки Лутбокса</h3>
        
        <div class="control-group">
          <label>Тип контейнера:</label>
          <select v-model="selectedChest" class="custom-select" :disabled="isOpening">
            <option value="scavenger_box">📦 Ящик Мусорщика (Обычный)</option>
            <option value="rusty_trunk">🚗 Багажник Ржавой Машины (Редкий)</option>
            <option value="hightech_cache">💾 Забытый Военный Сейф (Легендарный)</option>
          </select>
        </div>

        <div class="control-group">
          <label>Сноровка (Dexterity): {{ dexterity }}</label>
          <div class="slider-row">
            <input type="range" min="1" max="100" v-model.number="dexterity" class="custom-slider" :disabled="isOpening" />
            <span class="value-text">{{ dexEffect }} сек</span>
          </div>
          <span class="subtext">Уменьшает время физического вскрытия сундука.</span>
        </div>

        <button @click="startOpening" class="action-btn" :disabled="isOpening || isLooted">
          <span v-if="isOpening">🔓 Вскрытие... {{ openingProgress.toFixed(0) }}%</span>
          <span v-else-if="isLooted">📦 Контейнер пуст</span>
          <span v-else>🔑 Зажать ПКМ / Вскрыть</span>
        </button>

        <button v-if="isLooted" @click="resetChest" class="reset-btn">
          🔄 Закрыть контейнер на замок
        </button>
      </div>

      <!-- Физический процесс (Сундук) -->
      <div class="visualizer-panel">
        <div class="chest-arena" :class="{ 'shake-anim': isOpening && openingProgress > 10 }">
          <div v-if="!isLooted" class="chest-box" :class="selectedChest">
            <div class="lid"></div>
            <div class="lock"></div>
            <div class="body-part"></div>
          </div>

          <div v-else class="opened-chest-box" :class="selectedChest">
            <div class="lid-opened"></div>
            <div class="body-part"></div>
          </div>

          <!-- Анимация появления предмета -->
          <transition name="loot-pop">
            <div v-if="generatedItem" class="item-display" :class="generatedItem.rarity.id">
              <div class="item-glow"></div>
              <div class="item-icon-box">
                <span class="icon">{{ generatedItem.base.icon }}</span>
              </div>
              <div class="item-details-popup">
                <div class="tooltip-header" :style="{ color: generatedItem.rarity.color }">
                  {{ generatedItem.displayName }}
                </div>
                <div class="tooltip-rarity" :style="{ borderColor: generatedItem.rarity.color, color: generatedItem.rarity.color }">
                  {{ generatedItem.rarity.title }} {{ generatedItem.base.typeTitle }}
                </div>
                
                <div class="divider"></div>
                <div class="tooltip-stats">
                  <div v-for="(val, stat) in generatedItem.stats" :key="stat" class="stat-line">
                    <span class="stat-name">{{ statTitles[stat] }}</span>
                    <span class="stat-val">+{{ val.toFixed(1) }}</span>
                  </div>
                </div>
                
                <div class="divider" v-if="generatedItem.affixes.length"></div>
                <div class="tooltip-affixes" v-if="generatedItem.affixes.length">
                  <div class="affix-title">🔥 Активные Свойства:</div>
                  <div v-for="aff in generatedItem.affixes" :key="aff.id" class="affix-desc">
                    • <strong>{{ aff.title }}</strong>: {{ aff.desc }}
                  </div>
                </div>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <!-- Лог генерации под капотом -->
    <div class="log-section">
      <h3>📜 Лог отладки: Алгоритм Loot-Generator</h3>
      <div class="log-console" ref="consoleBox">
        <div v-for="(log, idx) in logs" :key="idx" class="log-line" :class="log.type">
          <span class="timestamp">[{{ log.time }}]</span>
          <span class="message" v-html="log.msg"></span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'LootGenerator',
  data() {
    return {
      selectedChest: 'scavenger_box',
      dexterity: 10,
      isOpening: false,
      isLooted: false,
      openingProgress: 0,
      generatedItem: null,
      logs: [],
      
      // Игровые данные
      chestsConfig: {
        scavenger_box: {
          baseTime: 3.0,
          title: "Ящик Мусорщика",
          lootTable: {
            rolls: 1,
            pools: [
              { weight: 70, baseId: 'rusty_pipe' },
              { weight: 25, baseId: 'crowbar' },
              { weight: 5, baseId: 'titanium_pick' }
            ],
            rarityWeights: { common: 80, rare: 18, epic: 2, legendary: 0 }
          }
        },
        rusty_trunk: {
          baseTime: 5.0,
          title: "Багажник Ржавой Машины",
          lootTable: {
            rolls: 1,
            pools: [
              { weight: 40, baseId: 'rusty_pipe' },
              { weight: 45, baseId: 'crowbar' },
              { weight: 15, baseId: 'titanium_pick' }
            ],
            rarityWeights: { common: 30, rare: 55, epic: 13, legendary: 2 }
          }
        },
        hightech_cache: {
          baseTime: 8.0,
          title: "Забытый Военный Сейф",
          lootTable: {
            rolls: 1,
            pools: [
              { weight: 5, baseId: 'rusty_pipe' },
              { weight: 25, baseId: 'crowbar' },
              { weight: 70, baseId: 'titanium_pick' }
            ],
            rarityWeights: { common: 0, rare: 10, epic: 50, legendary: 40 }
          }
        }
      },

      itemsBase: {
        rusty_pipe: { title: 'ржавая труба', icon: '🔧', gender: 'F', baseStats: { strength: 4, speed: 0.8 }, typeTitle: 'Дробящее' },
        crowbar: { title: 'фомка мусорщика', icon: '🔨', gender: 'F', baseStats: { strength: 7, speed: 1.1, dexterity: 2 }, typeTitle: 'Инструмент' },
        titanium_pick: { title: 'титановый резак', icon: '🪓', gender: 'M', baseStats: { strength: 14, speed: 1.5, dexterity: 5 }, typeTitle: 'Оружие-Инструмент' }
      },

      rarities: {
        common: { id: 'common', title: 'Обычный', color: '#a6adc8', affixSlots: 0 },
        rare: { id: 'rare', title: 'Редкий', color: '#89b4fa', affixSlots: 1 },
        epic: { id: 'epic', title: 'Эпический', color: '#cba6f7', affixSlots: 2 },
        legendary: { id: 'legendary', title: 'Легендарный', color: '#fab387', affixSlots: 3 }
      },

      affixes: [
        { id: 'sharp', title: 'Острый', translation: 'острый', stats: { strength: 3 }, type: 'PREFIX', desc: 'Увеличивает силу атаки на +3' },
        { id: 'heavy', title: 'Тяжелый', translation: 'тяжелый', stats: { strength: 6, speed: -0.2 }, type: 'PREFIX', desc: '+6 к силе, но -0.2 к скорости' },
        { id: 'nimble', title: 'Проворный', translation: 'проворный', stats: { speed: 0.3, dexterity: 4 }, type: 'PREFIX', desc: '+0.3 к скорости, +4 к Сноровке' },
        { id: 'scavenger', title: 'Кустарный', translation: 'кустарный', stats: { dexterity: 6 }, type: 'PREFIX', desc: 'Облегчает взлом (+6 к Сноровке)' }
      ],

      statTitles: {
        strength: '⚔️ Сила',
        speed: '⚡ Скорость',
        dexterity: '🤸 Сноровка'
      }
    };
  },
  computed: {
    dexEffect() {
      const base = this.chestsConfig[this.selectedChest].baseTime;
      return (base / (1.0 + this.dexterity * 0.1)).toFixed(2);
    }
  },
  methods: {
    addLog(msg, type = 'info') {
      const now = new Date();
      const time = now.toTimeString().split(' ')[0];
      this.logs.push({ time, msg, type });
      this.$nextTick(() => {
        const box = this.$refs.consoleBox;
        if (box) box.scrollTop = box.scrollHeight;
      });
    },
    startOpening() {
      this.isOpening = true;
      this.openingProgress = 0;
      const totalTime = parseFloat(this.dexEffect) * 1000;
      const intervalTime = 50;
      const steps = totalTime / intervalTime;
      let currentStep = 0;

      this.addLog(`🔧 Начато физическое вскрытие контейнера [${this.chestsConfig[this.selectedChest].title}]...`, 'warn');
      this.addLog(`🤸 Сноровка игрока: <strong>${this.dexterity}</strong>. Базовое время: ${this.chestsConfig[this.selectedChest].baseTime}с -> Финальное время: ${this.dexEffect}с.`, 'info');

      const timer = setInterval(() => {
        currentStep++;
        this.openingProgress = (currentStep / steps) * 10000 / 100;
        
        if (currentStep >= steps) {
          clearInterval(timer);
          this.isOpening = false;
          this.isLooted = true;
          this.generateLoot();
        }
      }, intervalTime);
    },
    generateLoot() {
      this.addLog(`🔓 Замок взломан! Начинается генерация лута по таблице...`, 'success');
      const chest = this.chestsConfig[this.selectedChest];
      const table = chest.lootTable;

      // 1. Выбираем редкость по весам
      const rarityId = this.rollRarity(table.rarityWeights);
      const rarity = this.rarities[rarityId];
      this.addLog(`🎲 Шаг 1: Ролл Редкости $\\to$ <strong><span style="color: ${rarity.color}">${rarity.title}</span></strong> (Шансы пула: Common ${table.rarityWeights.common}%, Rare ${table.rarityWeights.rare}%, Epic ${table.rarityWeights.epic}%, Legendary ${table.rarityWeights.legendary}%)`, 'info');

      // 2. Выбираем предмет из пула по весам
      const baseId = this.rollBaseItem(table.pools);
      const base = this.itemsBase[baseId];
      this.addLog(`🎲 Шаг 2: Ролл Базового предмета $\\to$ <strong>${base.title}</strong> (Род в ru_ru: ${base.gender === 'M' ? 'Мужской' : 'Женский'})`, 'info');

      // 3. Выбираем случайные аффиксы
      const activeAffixes = [];
      const numSlots = rarity.affixSlots;
      if (numSlots > 0) {
        const available = [...this.affixes];
        for (let i = 0; i < numSlots; i++) {
          if (available.length === 0) break;
          const idx = Math.floor(Math.random() * available.length);
          activeAffixes.push(available.splice(idx, 1)[0]);
        }
        this.addLog(`🔥 Шаг 3: Наложение аффиксов (Ячейки: ${numSlots}) $\\to$ Наложено свойств: ${activeAffixes.map(a => a.title).join(', ')}`, 'warn');
      } else {
        this.addLog(`🔥 Шаг 3: Наложение аффиксов $\\to$ Пропущено (Обычный предмет не имеет ячеек)`, 'info');
      }

      // 4. Лингвистическое согласование родов
      let displayName = '';
      const prefixes = activeAffixes.filter(a => a.type === 'PREFIX');
      
      prefixes.forEach(p => {
        const adjusted = this.applyGender(p.translation, base.gender);
        displayName += this.capitalize(adjusted) + ' ';
      });

      displayName += this.capitalize(base.title);

      const suffixes = activeAffixes.filter(a => a.type === 'SUFFIX');
      suffixes.forEach(s => {
        displayName += ' ' + this.capitalize(s.translation);
      });

      this.addLog(`🗣️ Шаг 4: Лингвистический движок (Russian Gender Agreement):`, 'info');
      prefixes.forEach(p => {
        this.addLog(`&nbsp;&nbsp;&nbsp;&nbsp;• Согласование прилагательного: [${p.translation}] (Муж. род) $\\to$ [${this.applyGender(p.translation, base.gender)}] (${base.gender === 'M' ? 'Муж.' : 'Жен.'} род для "${base.title}")`, 'success');
      });

      this.addLog(`💎 Итоговый предмет успешно создан: <strong style="color: ${rarity.color}">${displayName}</strong>!`, 'success');

      // Считаем финальные статы
      const finalStats = { ...base.baseStats };
      activeAffixes.forEach(aff => {
        Object.keys(aff.stats).forEach(stat => {
          if (finalStats[stat] !== undefined) {
            finalStats[stat] += aff.stats[stat];
          } else {
            finalStats[stat] = aff.stats[stat];
          }
        });
      });

      this.generatedItem = {
        base,
        rarity,
        affixes: activeAffixes,
        displayName,
        stats: finalStats
      };
    },
    rollRarity(weights) {
      const rand = Math.random() * 100;
      let current = 0;
      
      if (rand < (current += weights.common)) return 'common';
      if (rand < (current += weights.rare)) return 'rare';
      if (rand < (current += weights.epic)) return 'epic';
      return 'legendary';
    },
    rollBaseItem(pools) {
      const totalWeight = pools.reduce((sum, p) => sum + p.weight, 0);
      const rand = Math.random() * totalWeight;
      let current = 0;
      for (const pool of pools) {
        current += pool.weight;
        if (rand <= current) return pool.baseId;
      }
      return pools[0].baseId;
    },
    applyGender(adjective, gender) {
      const root = adjective.substring(0, adjective.length - 2);
      if (gender === 'M') {
        return adjective; // Мужской род: острый
      } else if (gender === 'F') {
        return root + 'ая'; // Женский род: острая
      } else {
        return root + 'ое'; // Средний род: острое
      }
    },
    capitalize(str) {
      return str.charAt(0).toUpperCase() + str.slice(1);
    },
    resetChest() {
      this.isLooted = false;
      this.generatedItem = null;
      this.addLog(`🔒 Контейнер заперт на замок. Снаряжение обновлено.`, 'info');
    }
  },
  mounted() {
    this.addLog("⚙️ Запущен симулятор RPG-Stat Engine & Loot-Generator v1.0", "info");
  }
};
</script>

<style scoped>
.loot-generator-box {
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
  background: linear-gradient(135deg, #cba6f7, #89b4fa);
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

.control-group {
  margin-bottom: 16px;
}

.control-group label {
  display: block;
  color: #bac2de;
  font-size: 0.85rem;
  margin-bottom: 6px;
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

.slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.custom-slider {
  flex: 1;
  accent-color: #cba6f7;
  cursor: pointer;
}

.value-text {
  background: #11111b;
  border: 1px solid #313244;
  border-radius: 4px;
  padding: 2px 8px;
  font-family: monospace;
  font-size: 0.85rem;
  color: #fab387;
  min-width: 60px;
  text-align: center;
}

.subtext {
  display: block;
  font-size: 0.75rem;
  color: #7f849c;
  margin-top: 4px;
}

.action-btn {
  width: 100%;
  background: linear-gradient(135deg, #cba6f7, #89b4fa);
  color: #11111b;
  border: none;
  font-weight: bold;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.95rem;
  transition: all 0.2s ease;
  margin-top: 10px;
}

.action-btn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(203, 166, 247, 0.3);
}

.action-btn:disabled {
  background: #313244;
  color: #585b70;
  cursor: not-allowed;
}

.reset-btn {
  width: 100%;
  background: transparent;
  border: 1px dashed #f38ba8;
  color: #f38ba8;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  margin-top: 10px;
  font-size: 0.85rem;
  transition: background 0.2s;
}

.reset-btn:hover {
  background: rgba(243, 139, 168, 0.1);
}

.visualizer-panel {
  background: rgba(17, 17, 27, 0.3);
  border: 1px solid rgba(49, 50, 68, 0.3);
  border-radius: 10px;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  min-height: 280px;
  overflow: hidden;
}

.chest-arena {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* Стилизация 3D-сундуков (плоские аналоги на CSS) */
.chest-box, .opened-chest-box {
  width: 100px;
  height: 80px;
  position: relative;
  transition: transform 0.3s;
}

.chest-box .body-part, .opened-chest-box .body-part {
  width: 100%;
  height: 50px;
  background: #583c24;
  border: 4px solid #3b2818;
  border-radius: 0 0 8px 8px;
  position: absolute;
  bottom: 0;
}

.chest-box .lid {
  width: 100%;
  height: 30px;
  background: #845c3c;
  border: 4px solid #3b2818;
  border-radius: 8px 8px 0 0;
  position: absolute;
  top: 0;
  transition: transform 0.2s;
}

.opened-chest-box .lid-opened {
  width: 100%;
  height: 30px;
  background: #845c3c;
  border: 4px solid #3b2818;
  border-radius: 8px 8px 0 0;
  position: absolute;
  top: -25px;
  transform: rotate(-45deg) translate(-10px, -10px);
}

/* Градации сундуков */
.scavenger_box .body-part { background: #45475a; border-color: #313244; }
.scavenger_box .lid, .scavenger_box .lid-opened { background: #585b70; border-color: #313244; }

.rusty_trunk .body-part { background: #a6e3a1; border-color: #94e2d5; }
.rusty_trunk .lid, .rusty_trunk .lid-opened { background: #89dceb; border-color: #94e2d5; }

.hightech_cache .body-part { background: #b4befe; border-color: #74c7ec; }
.hightech_cache .lid, .hightech_cache .lid-opened { background: #cba6f7; border-color: #74c7ec; }

.shake-anim {
  animation: shake 0.15s infinite;
}

@keyframes shake {
  0% { transform: translate(2px, 1px) rotate(0deg); }
  10% { transform: translate(-1px, -2px) rotate(-1deg); }
  20% { transform: translate(-3px, 0px) rotate(1deg); }
  30% { transform: translate(0px, 2px) rotate(0deg); }
  40% { transform: translate(1px, -1px) rotate(1deg); }
  50% { transform: translate(-1px, 2px) rotate(-1deg); }
  60% { transform: translate(-3px, 1px) rotate(0deg); }
  70% { transform: translate(2px, 1px) rotate(-1deg); }
  80% { transform: translate(-1px, -1px) rotate(1deg); }
  90% { transform: translate(2px, 2px) rotate(0deg); }
  100% { transform: translate(1px, -2px) rotate(-1deg); }
}

/* Вылетающий предмет */
.item-display {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: float 2s infinite ease-in-out;
}

@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
  100% { transform: translateY(0px); }
}

.item-icon-box {
  width: 70px;
  height: 70px;
  background: #11111b;
  border-radius: 50%;
  border: 3px solid #cba6f7;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 2.2rem;
  z-index: 2;
  box-shadow: 0 0 20px rgba(203, 166, 247, 0.4);
}

.item-glow {
  position: absolute;
  width: 90px;
  height: 90px;
  background: radial-gradient(circle, rgba(203, 166, 247, 0.25) 0%, transparent 70%);
  z-index: 1;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.2); opacity: 0.4; }
  100% { transform: scale(1); opacity: 0.8; }
}

/* Градации редкости */
.item-display.common .item-icon-box { border-color: #a6adc8; box-shadow: 0 0 20px rgba(166, 173, 200, 0.2); }
.item-display.common .item-glow { background: radial-gradient(circle, rgba(166, 173, 200, 0.15) 0%, transparent 70%); }

.item-display.rare .item-icon-box { border-color: #89b4fa; box-shadow: 0 0 20px rgba(137, 180, 250, 0.35); }
.item-display.rare .item-glow { background: radial-gradient(circle, rgba(137, 180, 250, 0.22) 0%, transparent 70%); }

.item-display.epic .item-icon-box { border-color: #cba6f7; box-shadow: 0 0 20px rgba(203, 166, 247, 0.5); }
.item-display.epic .item-glow { background: radial-gradient(circle, rgba(203, 166, 247, 0.35) 0%, transparent 70%); }

.item-display.legendary .item-icon-box { border-color: #fab387; box-shadow: 0 0 25px rgba(250, 179, 135, 0.6); }
.item-display.legendary .item-glow { background: radial-gradient(circle, rgba(250, 179, 135, 0.45) 0%, transparent 70%); }

/* Тултип предмета */
.item-details-popup {
  background: #11111b;
  border: 1px solid #313244;
  border-radius: 8px;
  padding: 12px;
  width: 250px;
  margin-top: 15px;
  z-index: 5;
  box-shadow: 0 10px 30px rgba(0,0,0,0.5);
  pointer-events: none;
}

.tooltip-header {
  font-weight: bold;
  font-size: 1rem;
  margin-bottom: 2px;
}

.tooltip-rarity {
  font-size: 0.75rem;
  text-transform: uppercase;
  font-weight: 800;
  letter-spacing: 0.5px;
  border: 1px solid;
  border-radius: 4px;
  padding: 1px 6px;
  display: inline-block;
  margin-bottom: 6px;
}

.divider {
  height: 1px;
  background: #313244;
  margin: 8px 0;
}

.tooltip-stats {
  font-size: 0.85rem;
}

.stat-line {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  color: #cdd6f4;
}

.stat-val {
  font-weight: bold;
  color: #a6e3a1;
}

.tooltip-affixes {
  font-size: 0.8rem;
}

.affix-title {
  color: #f9e2af;
  font-weight: bold;
  margin-bottom: 4px;
}

.affix-desc {
  color: #bac2de;
  margin-bottom: 3px;
  line-height: 1.2;
}

/* Анимация переходов */
.loot-pop-enter-active {
  animation: pop-in 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes pop-in {
  0% { transform: scale(0) translateY(50px); opacity: 0; }
  100% { transform: scale(1) translateY(0); opacity: 1; }
}

/* Логи консоли */
.log-section {
  background: #11111b;
  border: 1px solid #313244;
  border-radius: 10px;
  padding: 16px;
}

.log-section h3 {
  margin-top: 0;
  margin-bottom: 12px;
  color: #f9e2af;
  font-size: 0.95rem;
}

.log-console {
  background: #010102;
  border-radius: 6px;
  height: 180px;
  overflow-y: auto;
  padding: 12px;
  font-family: 'Fira Code', monospace;
  font-size: 0.8rem;
  border: 1px solid rgba(49, 50, 68, 0.5);
}

.log-line {
  margin-bottom: 6px;
  line-height: 1.4;
}

.log-line.info { color: #bac2de; }
.log-line.warn { color: #f9e2af; }
.log-line.error { color: #f38ba8; }
.log-line.success { color: #a6e3a1; }

.timestamp {
  color: #585b70;
  margin-right: 8px;
}
</style>
