<template>
  <div class="markdown-body">
    <!-- Парсим контент по элементам для динамического встраивания Vue-компонентов -->
    <div v-for="(block, idx) in parsedBlocks" :key="idx">
      <!-- 1. Интерактивные виджеты -->
      <div v-if="block.type === 'widget'" class="widget-wrapper">
        <SpringSimulator v-if="block.name === 'SPRING_SIMULATOR'" />
        <VertexCompressor v-if="block.name === 'VERTEX_COMPRESSOR'" />
        <TextEffects v-if="block.name === 'TEXT_EFFECTS'" />
      </div>

      <!-- 2. Специфические GitHub-алерты -->
      <div v-else-if="block.type === 'alert'" :class="['alert-box', `alert-${block.alertType}`]">
        <div class="alert-title">
          <span class="alert-icon">{{ getAlertIcon(block.alertType) }}</span>
          <span>{{ block.alertType.toUpperCase() }}</span>
        </div>
        <p class="alert-content" v-html="renderInlineMarkdown(block.content)"></p>
      </div>

      <!-- 3. Обычные блоки контента (текст, код, списки) рендерим как HTML -->
      <div v-else v-html="block.html" class="html-block"></div>
    </div>

    <!-- Встраиваемый Квиз в самом конце главы -->
    <div v-if="quiz && quiz.length" class="chapter-quiz-section">
      <QuizComponent :questions="quiz" />
    </div>
  </div>
</template>

<script>
import { nextTick } from 'vue';
import SpringSimulator from './SpringSimulator.vue';
import VertexCompressor from './VertexCompressor.vue';
import TextEffects from './TextEffects.vue';
import QuizComponent from './QuizComponent.vue';

export default {
  name: 'MarkdownRenderer',
  components: {
    SpringSimulator,
    VertexCompressor,
    TextEffects,
    QuizComponent
  },
  props: {
    content: {
      type: String,
      required: true
    },
    quiz: {
      type: Array,
      default: () => null
    }
  },
  computed: {
    parsedBlocks() {
      if (!this.content) return [];
      
      const blocks = [];
      const lines = this.content.split('\n');
      let currentTextBlock = [];
      let inCodeBlock = false;
      let codeLanguage = '';
      let codeContent = [];
      let inAlert = false;
      let alertType = '';
      let alertContent = [];

      const flushText = () => {
        if (currentTextBlock.length > 0) {
          blocks.push({
            type: 'html',
            html: this.renderMarkdownParagraphs(currentTextBlock.join('\n'))
          });
          currentTextBlock = [];
        }
      };

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];

        // 1. Обработка блоков кода ( ```java )
        if (line.trim().startsWith('```')) {
          if (inCodeBlock) {
            // Закрываем блок кода
            inCodeBlock = false;
            flushText();
            
            // Защита от экранирования XML в Prism.js
            const escapedCode = this.escapeHtml(codeContent.join('\n'));
            blocks.push({
              type: 'html',
              html: `<pre class="line-numbers"><code class="language-${codeLanguage}">${escapedCode}</code></pre>`
            });
            codeContent = [];
          } else {
            // Открываем блок кода
            flushText();
            inCodeBlock = true;
            codeLanguage = line.trim().replace('```', '').toLowerCase() || 'javascript';
          }
          continue;
        }

        if (inCodeBlock) {
          codeContent.push(line);
          continue;
        }

        // 2. Обработка GitHub-алертов ( > [!NOTE] )
        if (line.trim().startsWith('>') && (line.includes('[!NOTE]') || line.includes('[!WARNING]') || line.includes('[!IMPORTANT]') || line.includes('[!TIP]') || line.includes('[!CAUTION]'))) {
          flushText();
          inAlert = true;
          alertType = line.match(/\[!([A-Z]+)\]/)[1].toLowerCase();
          alertContent = [];
          continue;
        }

        if (inAlert) {
          if (line.trim().startsWith('>')) {
            // Убираем символ цитаты '>'
            const cleanedLine = line.replace(/^\s*>\s?/, '');
            alertContent.push(cleanedLine);
            continue;
          } else {
            // Закрываем алерт
            inAlert = false;
            blocks.push({
              type: 'alert',
              alertType: alertType,
              content: alertContent.join('\n')
            });
            alertContent = [];
          }
        }

        // 3. Обработка маркеров виджетов
        const widgetMatch = line.trim().match(/^\[WIDGET:([A-Z_]+)\]$/);
        if (widgetMatch) {
          flushText();
          blocks.push({
            type: 'widget',
            name: widgetMatch[1]
          });
          continue;
        }

        // Накопление обычных строк
        currentTextBlock.push(line);
      }

      // Сливаем остатки
      if (inAlert) {
        blocks.push({
          type: 'alert',
          alertType: alertType,
          content: alertContent.join('\n')
        });
      }
      flushText();

      return blocks;
    }
  },
  watch: {
    content() {
      this.triggerHighlight();
    }
  },
  mounted() {
    this.triggerHighlight();
    this.$el.addEventListener('click', this.handleLinkClick);
  },
  beforeUnmount() {
    this.$el.removeEventListener('click', this.handleLinkClick);
  },
  methods: {
    triggerHighlight() {
      nextTick(() => {
        if (window.Prism) {
          window.Prism.highlightAll();
        }
      });
    },
    handleLinkClick(e) {
      const target = e.target.closest('a');
      if (!target) return;
      
      const href = target.getAttribute('href');
      if (href && href.endsWith('.md')) {
        e.preventDefault();
        const fileName = href.replace('.md', '');
        this.$emit('navigate-chapter', fileName);
      }
    },
    escapeHtml(text) {
      return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    },
    getAlertIcon(type) {
      const icons = {
        note: 'ℹ️',
        important: '📢',
        warning: '⚠️',
        tip: '💡',
        caution: '🛑'
      };
      return icons[type] || 'ℹ️';
    },
    renderInlineMarkdown(text) {
      if (!text) return '';
      let html = this.escapeHtml(text);
      
      // Ссылки: [text](url)
      html = html.replace(/\[(.*?)\]\((.*?)\)/g, '<a href="$2" class="md-link">$1</a>');
      // Жирный: **text**
      html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
      // Курсив: *text*
      html = html.replace(/\*(.*?)\*/g, '<em>$1</em>');
      // Моноширинный inline-код: `code`
      html = html.replace(/`(.*?)`/g, '<code class="inline-code">$1</code>');
      // LaTeX формулы простые: $formula$
      html = html.replace(/\$(.*?)\$/g, '<span class="latex-inline">$1</span>');
      
      return html;
    },
    renderMarkdownParagraphs(text) {
      if (!text) return '';
      
      const lines = text.split('\n');
      const htmlLines = [];
      let inList = false;

      for (let i = 0; i < lines.length; i++) {
        let line = lines[i].trim();

        if (!line) {
          if (inList) {
            htmlLines.push('</ul>');
            inList = false;
          }
          continue;
        }

        // Горизонтальный разделитель ---
        if (line === '---') {
          if (inList) {
            htmlLines.push('</ul>');
            inList = false;
          }
          htmlLines.push('<hr class="md-hr" />');
          continue;
        }

        // Заголовки #
        if (line.startsWith('# ')) {
          if (inList) { htmlLines.push('</ul>'); inList = false; }
          htmlLines.push(`<h1 class="md-h1">${this.renderInlineMarkdown(line.replace('# ', ''))}</h1>`);
          continue;
        }

        // Заголовки ##
        if (line.startsWith('## ')) {
          if (inList) { htmlLines.push('</ul>'); inList = false; }
          const title = line.replace('## ', '');
          const id = title.toLowerCase().replace(/[^a-z0-9а-яё\s-]/g, '').trim().replace(/\s+/g, '_');
          htmlLines.push(`<h2 id="${id}" class="md-h2">${this.renderInlineMarkdown(title)}</h2>`);
          continue;
        }

        // Заголовки ###
        if (line.startsWith('### ')) {
          if (inList) { htmlLines.push('</ul>'); inList = false; }
          htmlLines.push(`<h3 class="md-h3">${this.renderInlineMarkdown(line.replace('### ', ''))}</h3>`);
          continue;
        }

        // Списки - или *
        if (line.startsWith('- ') || line.startsWith('* ')) {
          if (!inList) {
            htmlLines.push('<ul class="md-ul">');
            inList = true;
          }
          htmlLines.push(`<li>${this.renderInlineMarkdown(line.replace(/^[-*]\s/, ''))}</li>`);
          continue;
        }

        // Обычные абзацы
        if (inList) {
          htmlLines.push('</ul>');
          inList = false;
        }
        
        htmlLines.push(`<p class="md-p">${this.renderInlineMarkdown(line)}</p>`);
      }

      if (inList) {
        htmlLines.push('</ul>');
      }

      return htmlLines.join('\n');
    }
  }
};
</script>

<style>
/* ─── СТИЛИ ДЛЯ КРАСИВОГО MARKDOWN (STACKEDIT STYLE) ─── */

.markdown-body {
  color: #c9ccd6;
  font-size: 15px;
  line-height: 1.65;
  font-weight: 400;
}

.md-h1 {
  color: #ffffff;
  font-size: 26px;
  font-weight: 600;
  margin-top: 0;
  margin-bottom: 24px;
  border-bottom: 1px solid #22252e;
  padding-bottom: 12px;
}

.md-h2 {
  color: #ffffff;
  font-size: 20px;
  font-weight: 600;
  margin-top: 40px;
  margin-bottom: 16px;
  border-bottom: 1px solid #22252e;
  padding-bottom: 8px;
  scroll-margin-top: 80px; /* Чтобы при прокрутке к якорю заголовок не скрывался под шапкой */
}

.md-h3 {
  color: #06b6d4; /* Красивый бирюзовый тон */
  font-size: 16px;
  font-weight: 600;
  margin-top: 24px;
  margin-bottom: 12px;
}

.md-p {
  margin: 0 0 16px 0;
}

.md-ul {
  margin: 0 0 16px 0;
  padding-left: 20px;
}

.md-ul li {
  margin-bottom: 8px;
}

.inline-code {
  background-color: #1b1c24;
  border: 1px solid #282c37;
  color: #e06c75;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
}

.latex-inline {
  font-family: 'Times New Roman', Georgia, serif;
  font-style: italic;
  background-color: rgba(59, 130, 246, 0.04);
  color: #60a5fa;
  padding: 0 4px;
  border-radius: 3px;
}

/* Красивая стилизация блоков кода Prism.js */
pre[class*="language-"] {
  background-color: #0b0c10 !important;
  border: 1px solid #22252e !important;
  border-radius: 6px !important;
  padding: 16px !important;
  margin: 20px 0 !important;
  overflow-x: auto;
}

code[class*="language-"] {
  font-family: 'JetBrains Mono', monospace !important;
  font-size: 13px !important;
  line-height: 1.5 !important;
}

/* Стилизация GitHub Alerts */
.alert-box {
  background-color: #1b1c24;
  border-left: 4px solid #3b82f6;
  border-radius: 0 6px 6px 0;
  padding: 16px;
  margin: 24px 0;
}

.alert-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: bold;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.alert-icon {
  font-size: 14px;
}

.alert-content {
  margin: 0;
  font-size: 13.5px;
  color: #a0a6b5;
  line-height: 1.5;
}

/* Цветовые схемы алертов */
.alert-note { border-left-color: #3b82f6; }
.alert-note .alert-title { color: #3b82f6; }

.alert-important { border-left-color: #8b5cf6; }
.alert-important .alert-title { color: #8b5cf6; }

.alert-warning { border-left-color: #f59e0b; }
.alert-warning .alert-title { color: #f59e0b; }

.alert-tip { border-left-color: #10b981; }
.alert-tip .alert-title { color: #10b981; }

.alert-caution { border-left-color: #ef4444; }
.alert-caution .alert-title { color: #ef4444; }

.widget-wrapper {
  margin: 28px 0;
}

.chapter-quiz-section {
  border-top: 1px dashed #22252e;
  padding-top: 32px;
  margin-top: 48px;
}

.md-hr {
  border: 0;
  height: 1px;
  background-color: #22252e;
  margin: 32px 0;
}

.md-link {
  color: #3b82f6;
  text-decoration: none;
  border-bottom: 1px dashed rgba(59, 130, 246, 0.4);
  transition: all 0.15s ease;
  cursor: pointer;
}

.md-link:hover {
  color: #60a5fa;
  border-bottom-style: solid;
  border-bottom-color: #60a5fa;
}
</style>
