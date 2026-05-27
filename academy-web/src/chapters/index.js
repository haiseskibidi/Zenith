import readme from '../../../docs/academy/README.md?raw';
import chapter0 from '../../../docs/academy/chapter_0_algorithms_for_beginners.md?raw';
import chapter1 from '../../../docs/academy/chapter_1_jvm_and_memory.md?raw';
import chapter2 from '../../../docs/academy/chapter_2_opengl_pipeline.md?raw';
import chapter3 from '../../../docs/academy/chapter_3_gpu_driven_rendering.md?raw';
import chapter4 from '../../../docs/academy/chapter_4_procedural_physics_and_math.md?raw';
import chapter5 from '../../../docs/academy/chapter_5_event_driven_architecture.md?raw';
import chapter6 from '../../../docs/academy/chapter_6_ui_and_font_rendering.md?raw';
import chapter7 from '../../../docs/academy/chapter_7_concurrency_and_threading.md?raw';
import chapter9 from '../../../docs/academy/chapter_9_rpg_and_loot_generation.md?raw';
import chapter10 from '../../../docs/academy/chapter_10_viewmodel_physics_and_magnetism.md?raw';
import chapter8 from '../../../docs/academy/chapter_8_interview_cheat_sheet.md?raw';

import { quizzes } from './quizzes/index.js';

// Функция для авто-парсинга разделов (заголовков ##) из Markdown текста
function parseSections(markdownText) {
  const sections = [];
  const lines = markdownText.split('\n');
  
  // Добавляем дефолтный раздел для введения, если заголовков нет
  let currentSection = { id: 'intro', title: 'Введение', content: '' };
  
  for (const line of lines) {
    if (line.startsWith('## ')) {
      // Сохраняем предыдущий раздел, если в нем был контент
      if (currentSection.content.trim()) {
        sections.push({ ...currentSection });
      }
      
      const title = line.replace('## ', '').trim();
      const id = title
        .toLowerCase()
        .replace(/[^a-z0-9а-яё\s-]/g, '') // Оставляем только буквы, цифры и пробелы
        .trim()
        .replace(/\s+/g, '_'); // Заменяем пробелы на подчеркивания
        
      currentSection = { id, title, content: '' };
    } else {
      currentSection.content += line + '\n';
    }
  }
  
  if (currentSection.content.trim()) {
    sections.push(currentSection);
  }
  
  return sections;
}

export const chapters = [
  {
    id: "readme",
    title: "Введение & Карта курса",
    rawContent: readme,
    sections: parseSections(readme),
    quiz: null
  },
  {
    id: "chapter_0",
    title: "Глава 0. Основы алгоритмов",
    rawContent: chapter0,
    sections: parseSections(chapter0),
    quiz: quizzes.chapter_0
  },
  {
    id: "chapter_1",
    title: "Глава 1. Управление памятью JVM",
    rawContent: chapter1,
    sections: parseSections(chapter1),
    quiz: quizzes.chapter_1
  },
  {
    id: "chapter_2",
    title: "Глава 2. Графический конвейер OpenGL",
    rawContent: chapter2,
    sections: parseSections(chapter2),
    quiz: quizzes.chapter_2
  },
  {
    id: "chapter_3",
    title: "Глава 3. GPU-Driven Rendering",
    rawContent: chapter3,
    sections: parseSections(chapter3),
    quiz: quizzes.chapter_3
  },
  {
    id: "chapter_4",
    title: "Глава 4. Процедурная физика & IK",
    rawContent: chapter4,
    sections: parseSections(chapter4),
    quiz: quizzes.chapter_4
  },
  {
    id: "chapter_5",
    title: "Глава 5. Реактивная шина событий",
    rawContent: chapter5,
    sections: parseSections(chapter5),
    quiz: quizzes.chapter_5
  },
  {
    id: "chapter_6",
    title: "Глава 6. Рендеринг интерфейсов & Шрифты",
    rawContent: chapter6,
    sections: parseSections(chapter6),
    quiz: quizzes.chapter_6
  },
  {
    id: "chapter_7",
    title: "Глава 7. Асинхронность & Многопоточность",
    rawContent: chapter7,
    sections: parseSections(chapter7),
    quiz: quizzes.chapter_7
  },
  {
    id: "chapter_9",
    title: "Глава 9. RPG-система & Генератор Лута",
    rawContent: chapter9,
    sections: parseSections(chapter9),
    quiz: quizzes.chapter_9
  },
  {
    id: "chapter_10",
    title: "Глава 10. Физика Viewmodel & Магнитный Лут",
    rawContent: chapter10,
    sections: parseSections(chapter10),
    quiz: quizzes.chapter_10
  },
  {
    id: "chapter_8",
    title: "Глава 8. Шпаргалка IT-собеседований",
    rawContent: chapter8,
    sections: parseSections(chapter8),
    quiz: quizzes.chapter_8
  }
];
