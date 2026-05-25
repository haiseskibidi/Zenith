<template>
  <div class="quiz-container">
    <div class="quiz-header">
      <span class="quiz-badge">Проверка знаний</span>
      <span class="quiz-progress">Вопрос {{ currentQuestionIndex + 1 }} из {{ questions.length }}</span>
    </div>
    
    <div class="quiz-progress-bar">
      <div 
        class="quiz-progress-fill" 
        :style="{ width: ((currentQuestionIndex) / questions.length) * 100 + '%' }"
      ></div>
    </div>

    <div v-if="!showScore" class="quiz-card">
      <h3 class="quiz-question">{{ currentQuestion.question }}</h3>
      
      <div class="quiz-options">
        <button 
          v-for="(option, index) in currentQuestion.options" 
          :key="index"
          @click="selectOption(index)"
          :disabled="selectedAnswer !== null"
          :class="[
            'option-btn',
            { 
              'selected-correct': selectedAnswer !== null && index === currentQuestion.correctIndex,
              'selected-wrong': selectedAnswer !== null && selectedAnswer === index && index !== currentQuestion.correctIndex,
              'not-selected': selectedAnswer !== null && index !== selectedAnswer && index !== currentQuestion.correctIndex
            }
          ]"
        >
          <span class="option-marker">{{ String.fromCharCode(65 + index) }}</span>
          <span class="option-text">{{ option }}</span>
        </button>
      </div>

      <!-- Объяснение ответа -->
      <div v-if="selectedAnswer !== null" class="explanation-box">
        <div class="explanation-header">
          <span v-if="selectedAnswer === currentQuestion.correctIndex" class="status-correct">✓ Правильно!</span>
          <span v-else class="status-wrong">✗ Неверно</span>
        </div>
        <p class="explanation-text">{{ currentQuestion.explanation }}</p>
        
        <button @click="nextQuestion" class="next-btn">
          {{ currentQuestionIndex < questions.length - 1 ? 'Следующий вопрос' : 'Показать результат' }} →
        </button>
      </div>
    </div>

    <!-- Результат -->
    <div v-else class="score-card">
      <div class="score-radial">
        <span class="score-number">{{ score }} / {{ questions.length }}</span>
      </div>
      <h3 class="score-title">Тест пройден!</h3>
      <p class="score-desc">
        {{ getScoreMessage() }}
      </p>
      <button @click="resetQuiz" class="reset-btn">Пройти еще раз</button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'QuizComponent',
  props: {
    questions: {
      type: Array,
      required: true
    }
  },
  data() {
    return {
      currentQuestionIndex: 0,
      selectedAnswer: null,
      score: 0,
      showScore: false
    };
  },
  computed: {
    currentQuestion() {
      return this.questions[this.currentQuestionIndex];
    }
  },
  methods: {
    selectOption(index) {
      this.selectedAnswer = index;
      if (index === this.currentQuestion.correctIndex) {
        this.score++;
      }
    },
    nextQuestion() {
      if (this.currentQuestionIndex < this.questions.length - 1) {
        this.currentQuestionIndex++;
        this.selectedAnswer = null;
      } else {
        this.showScore = true;
      }
    },
    resetQuiz() {
      this.currentQuestionIndex = 0;
      this.selectedAnswer = null;
      this.score = 0;
      this.showScore = false;
    },
    getScoreMessage() {
      const percentage = (this.score / this.questions.length) * 100;
      if (percentage === 100) return 'Идеальный результат! Ты отлично освоил материал главы и мыслишь как Senior-разработчик Zenith.';
      if (percentage >= 60) return 'Хороший результат! Базовые концепции усвоены, ты готов переходить к следующей главе или попробовать улучшить счет.';
      return 'Тема оказалась непростой. Рекомендуем перечитать эту главу и попробовать пройти квиз еще раз для закрепления.';
    }
  }
};
</script>

<style scoped>
.quiz-container {
  background-color: #16171d;
  border: 1px solid #22252e;
  border-radius: 8px;
  padding: 24px;
  margin: 32px 0;
}

.quiz-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.quiz-badge {
  background-color: #2a2e3b;
  color: #a3aabf;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.quiz-progress {
  color: #798299;
  font-size: 13px;
}

.quiz-progress-bar {
  background-color: #22252e;
  height: 4px;
  border-radius: 2px;
  margin-bottom: 24px;
  overflow: hidden;
}

.quiz-progress-fill {
  background-color: #3b82f6;
  height: 100%;
  transition: width 0.3s ease;
}

.quiz-question {
  color: #ffffff;
  font-size: 18px;
  font-weight: 500;
  margin: 0 0 20px 0;
  line-height: 1.5;
}

.quiz-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.option-btn {
  background-color: #1b1c24;
  border: 1px solid #282c37;
  border-radius: 6px;
  padding: 14px 16px;
  color: #c9ccd6;
  text-align: left;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.2s ease;
  font-size: 14px;
  line-height: 1.4;
}

.option-btn:hover:not(:disabled) {
  border-color: #3b82f6;
  background-color: #1e2230;
}

.option-marker {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2a2e3b;
  color: #8c95a5;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  font-weight: 600;
  font-size: 12px;
}

.selected-correct {
  border-color: #10b981 !important;
  background-color: rgba(16, 185, 129, 0.08) !important;
  color: #10b981 !important;
}

.selected-correct .option-marker {
  background-color: #10b981 !important;
  color: #ffffff !important;
}

.selected-wrong {
  border-color: #ef4444 !important;
  background-color: rgba(239, 68, 68, 0.08) !important;
  color: #ef4444 !important;
}

.selected-wrong .option-marker {
  background-color: #ef4444 !important;
  color: #ffffff !important;
}

.not-selected {
  opacity: 0.5;
}

.explanation-box {
  background-color: #1b1c24;
  border-left: 3px solid #3b82f6;
  border-radius: 0 6px 6px 0;
  padding: 16px;
  margin-top: 20px;
}

.explanation-header {
  margin-bottom: 8px;
}

.status-correct {
  color: #10b981;
  font-weight: 600;
  font-size: 14px;
}

.status-wrong {
  color: #ef4444;
  font-weight: 600;
  font-size: 14px;
}

.explanation-text {
  color: #a0a6b5;
  font-size: 14px;
  line-height: 1.5;
  margin: 0 0 16px 0;
}

.next-btn {
  background-color: #3b82f6;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  padding: 10px 16px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s ease;
  font-size: 13px;
}

.next-btn:hover {
  background-color: #2563eb;
}

.score-card {
  text-align: center;
  padding: 20px 0;
}

.score-radial {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background-color: rgba(59, 130, 246, 0.08);
  border: 3px solid #3b82f6;
  color: #3b82f6;
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 16px;
}

.score-title {
  color: #ffffff;
  font-size: 20px;
  margin: 0 0 8px 0;
}

.score-desc {
  color: #a0a6b5;
  font-size: 14px;
  line-height: 1.5;
  max-width: 400px;
  margin: 0 auto 24px auto;
}

.reset-btn {
  background-color: #2a2e3b;
  color: #ffffff;
  border: 1px solid #3c4254;
  border-radius: 6px;
  padding: 10px 20px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 13px;
}

.reset-btn:hover {
  background-color: #323747;
  border-color: #4b5269;
}
</style>
