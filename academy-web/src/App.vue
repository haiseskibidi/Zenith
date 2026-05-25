<template>
  <div class="app-wrapper">
    <!-- БОКОВАЯ ПАНЕЛЬ (SIDEBAR) -->
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-logo">Z</span>
        <div class="brand-text">
          <span class="brand-name">Zenith</span>
          <span class="brand-sub">Academy Portal</span>
        </div>
      </div>

      <!-- Вкладки верхнего уровня -->
      <nav class="sidebar-tabs">
        <button 
          @click="currentTab = 'learn'" 
          :class="['tab-btn', { 'tab-active': currentTab === 'learn' }]"
        >
          📖 Обучение
        </button>
        <button 
          @click="currentTab = 'lab'" 
          :class="['tab-btn', { 'tab-active': currentTab === 'lab' }]"
        >
          🔬 Лаборатория
        </button>
      </nav>

      <!-- Навигация по главам (если вкладка - Обучение) -->
      <div v-if="currentTab === 'learn'" class="sidebar-menu">
        <span class="menu-title">Разделы курса:</span>
        <div class="menu-list">
          <button 
            v-for="(chapter, idx) in chapters" 
            :key="chapter.id"
            @click="selectChapter(idx)"
            :class="['menu-item', { 'menu-item-active': activeChapterIndex === idx }]"
          >
            <span class="menu-num">{{ idx === 0 ? '•' : idx }}</span>
            <span class="menu-label">{{ chapter.title.split('. ')[1] || chapter.title }}</span>
          </button>
        </div>

        <!-- Подразделы текущей главы для быстрой навигации -->
        <div v-if="currentChapter.sections && currentChapter.sections.length > 0" class="sub-menu-box">
          <span class="menu-title">Содержание главы:</span>
          <div class="sub-menu-list">
            <button 
              v-for="section in currentChapter.sections" 
              :key="section.id"
              @click="scrollToSection(section.id)"
              class="sub-menu-item"
            >
              # {{ section.title.split('. ')[1] || section.title }}
            </button>
          </div>
        </div>
      </div>

      <!-- Боковая панель Лаборатории -->
      <div v-else class="sidebar-menu">
        <span class="menu-title">Физические песочницы:</span>
        <div class="menu-list">
          <button 
            @click="activeLabWidget = 'spring'" 
            :class="['menu-item', { 'menu-item-active': activeLabWidget === 'spring' }]"
          >
            🪀 Симулятор пружины 2-го порядка
          </button>
          <button 
            @click="activeLabWidget = 'compressor'" 
            :class="['menu-item', { 'menu-item-active': activeLabWidget === 'compressor' }]"
          >
            🗜️ Сжиматель воксельных вершин
          </button>
          <button 
            @click="activeLabWidget = 'text'" 
            :class="['menu-item', { 'menu-item-active': activeLabWidget === 'text' }]"
          >
            🌈 Шейдерные эффекты текста
          </button>
        </div>
      </div>
      
      <!-- Подвал сайдбара -->
      <div class="sidebar-footer">
        <span>Zenith Engine v2.5</span>
      </div>
    </aside>

    <!-- ОСНОВНАЯ ОБЛАСТЬ (CONTENT) -->
    <main class="main-content">
      <!-- Верхний Header -->
      <header class="main-header">
        <div class="header-breadcrumbs">
          <span class="bc-parent">{{ currentTab === 'learn' ? 'Обучение' : 'Лаборатория' }}</span>
          <span class="bc-separator">/</span>
          <span class="bc-active">{{ currentTab === 'learn' ? currentChapter.title : getLabWidgetTitle() }}</span>
        </div>
        <div class="header-actions">
          <button @click="openDocsFolder" class="docs-folder-btn">
            📁 Открыть папку docs
          </button>
        </div>
      </header>

      <!-- Тело контента -->
      <div class="content-viewport" ref="scrollContainer">
        <!-- 1. Вкладка чтения и обучения -->
        <div v-if="currentTab === 'learn'" class="reader-view">
          <MarkdownRenderer 
            :content="currentChapter.rawContent" 
            :quiz="currentChapter.quiz"
            @navigate-chapter="onNavigateChapter"
          />
          
          <!-- Навигационный футер -->
          <div class="chapter-navigation">
            <button 
              @click="prevChapter" 
              :disabled="activeChapterIndex === 0"
              class="nav-btn"
            >
              ← Предыдущая глава
            </button>
            <button 
              @click="nextChapter" 
              :disabled="activeChapterIndex === chapters.length - 1"
              class="nav-btn"
            >
              Следующая глава →
            </button>
          </div>
        </div>

        <!-- 2. Вкладка Лаборатории (Песочницы) -->
        <div v-else class="lab-view">
          <div class="lab-intro-box">
            <h2 class="lab-title">Экспериментальная Лаборатория Zenith</h2>
            <p class="lab-desc">
              Здесь собраны интерактивные песочницы для наглядной демонстрации низкоуровневых процессов. Изменяй параметры, двигай мышь и наблюдай, как физические уравнения и побитовые структуры работают "под капотом".
            </p>
          </div>
          
          <SpringSimulator v-if="activeLabWidget === 'spring'" />
          <VertexCompressor v-if="activeLabWidget === 'compressor'" />
          <TextEffects v-if="activeLabWidget === 'text'" />
        </div>
      </div>
    </main>
  </div>
</template>

<script>
import { chapters } from './chapters/index.js';
import MarkdownRenderer from './components/MarkdownRenderer.vue';
import SpringSimulator from './components/SpringSimulator.vue';
import VertexCompressor from './components/VertexCompressor.vue';
import TextEffects from './components/TextEffects.vue';

export default {
  name: 'App',
  components: {
    MarkdownRenderer,
    SpringSimulator,
    VertexCompressor,
    TextEffects
  },
  data() {
    return {
      chapters: chapters,
      activeChapterIndex: 0,
      currentTab: 'learn', // 'learn' или 'lab'
      activeLabWidget: 'spring' // 'spring', 'compressor', 'text'
    };
  },
  computed: {
    currentChapter() {
      return this.chapters[this.activeChapterIndex];
    }
  },
  methods: {
    selectChapter(index) {
      this.activeChapterIndex = index;
      this.currentTab = 'learn';
      // Скроллим контент наверх при смене главы
      this.$nextTick(() => {
        this.$refs.scrollContainer.scrollTop = 0;
      });
    },
    prevChapter() {
      if (this.activeChapterIndex > 0) {
        this.selectChapter(this.activeChapterIndex - 1);
      }
    },
    nextChapter() {
      if (this.activeChapterIndex < this.chapters.length - 1) {
        this.selectChapter(this.activeChapterIndex + 1);
      }
    },
    scrollToSection(id) {
      if (id === 'intro') {
        this.$refs.scrollContainer.scrollTo({ top: 0, behavior: 'smooth' });
        return;
      }
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
      } else {
        // Резервный скролл наверх, если элемент не найден
        this.$refs.scrollContainer.scrollTo({ top: 0, behavior: 'smooth' });
      }
    },
    getLabWidgetTitle() {
      const titles = {
        spring: 'Симулятор пружины 2-го порядка',
        compressor: 'Сжиматель воксельных вершин',
        text: 'Шейдерные эффекты текста'
      };
      return titles[this.activeLabWidget] || 'Песочница';
    },
    onNavigateChapter(fileName) {
      const mapping = {
        'readme': 0,
        'README': 0,
        'chapter_1_jvm_and_memory': 1,
        'chapter_2_opengl_pipeline': 2,
        'chapter_3_gpu_driven_rendering': 3,
        'chapter_4_procedural_physics_and_math': 4,
        'chapter_5_event_driven_architecture': 5,
        'chapter_6_ui_and_font_rendering': 6,
        'chapter_7_concurrency_and_threading': 7,
        'chapter_8_interview_cheat_sheet': 8
      };
      const cleanFileName = fileName.replace(/^\.\//, '');
      const index = mapping[cleanFileName];
      if (index !== undefined) {
        this.selectChapter(index);
      }
    },
    async openDocsFolder() {
      try {
        await fetch('/api/open-docs');
      } catch (err) {
        console.error('Не удалось открыть папку docs:', err);
      }
    }
  }
};
</script>

<style scoped>
.app-wrapper {
  display: flex;
  width: 100vw;
  height: 100vh;
  background-color: #0b0c10;
  overflow: hidden;
}

/* ─── СТИЛИ SIDEBAR (STACKEDIT) ─── */
.sidebar {
  width: 280px;
  background-color: #111216;
  border-right: 1px solid #22252e;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .sidebar {
    width: 220px;
  }
}

.sidebar-brand {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #22252e;
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #3b82f6;
  color: #ffffff;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  font-weight: 700;
  font-size: 18px;
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-name {
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.brand-sub {
  color: #555d70;
  font-size: 11px;
}

.sidebar-tabs {
  display: flex;
  padding: 16px 20px 8px 20px;
  gap: 8px;
}

.tab-btn {
  flex: 1;
  background-color: transparent;
  border: 1px solid #22252e;
  border-radius: 4px;
  color: #798299;
  padding: 8px 0;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  border-color: #3b82f6;
  color: #ffffff;
}

.tab-active {
  background-color: #1b1c24;
  border-color: #3b82f6;
  color: #3b82f6;
}

.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Тонкая кастомизация скроллбара */
.sidebar-menu::-webkit-scrollbar {
  width: 4px;
}
.sidebar-menu::-webkit-scrollbar-thumb {
  background-color: #22252e;
  border-radius: 2px;
}

.menu-title {
  color: #4b5269;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: block;
  margin-bottom: 8px;
}

.menu-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-item {
  background-color: transparent;
  border: none;
  border-radius: 4px;
  color: #a0a6b5;
  padding: 10px 12px;
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.15s ease;
  line-height: 1.3;
}

.menu-item:hover {
  background-color: #16171d;
  color: #ffffff;
}

.menu-item-active {
  background-color: #16171d;
  color: #3b82f6 !important;
  font-weight: 500;
}

.menu-num {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #4b5269;
  width: 14px;
  display: inline-block;
}

.menu-item-active .menu-num {
  color: #3b82f6;
}

.sub-menu-box {
  border-top: 1px solid #22252e;
  padding-top: 16px;
}

.sub-menu-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sub-menu-item {
  background-color: transparent;
  border: none;
  color: #6c748c;
  padding: 6px 12px;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition: color 0.15s ease;
}

.sub-menu-item:hover {
  color: #ffffff;
}

.sidebar-footer {
  padding: 16px 24px;
  border-top: 1px solid #22252e;
  color: #3c4254;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
}

/* ─── ОСНОВНАЯ ОБЛАСТЬ (MAIN CONTENT) ─── */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background-color: #0b0c10;
}

.main-header {
  height: 64px;
  background-color: #111216;
  border-bottom: 1px solid #22252e;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 32px;
  flex-shrink: 0;
}

.header-breadcrumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
}

.bc-parent {
  color: #798299;
}

.bc-separator {
  color: #3c4254;
}

.bc-active {
  color: #ffffff;
  font-weight: 500;
}

.docs-folder-btn {
  background: transparent;
  border: none;
  color: #3b82f6;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  transition: color 0.15s ease;
}

.docs-folder-btn:hover {
  color: #60a5fa;
}

.content-viewport {
  flex: 1;
  overflow-y: auto;
  padding: 40px 10%;
  scroll-behavior: smooth; /* Плавная прокрутка к якорям */
}

@media (max-width: 1200px) {
  .content-viewport {
    padding: 40px 5%;
  }
}

.reader-view {
  max-width: 820px;
  margin: 0 auto;
}

.chapter-navigation {
  display: flex;
  justify-content: space-between;
  margin-top: 48px;
  border-top: 1px solid #22252e;
  padding-top: 24px;
}

.nav-btn {
  background-color: #111216;
  border: 1px solid #22252e;
  border-radius: 6px;
  color: #a0a6b5;
  padding: 10px 18px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-btn:hover:not(:disabled) {
  border-color: #3b82f6;
  color: #ffffff;
  background-color: #16171d;
}

.nav-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.lab-view {
  max-width: 900px;
  margin: 0 auto;
}

.lab-intro-box {
  margin-bottom: 28px;
}

.lab-title {
  color: #ffffff;
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.lab-desc {
  color: #798299;
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}
</style>
