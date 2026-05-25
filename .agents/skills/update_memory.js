// Описание для ИИ (Tool Description):
// "Используй эту функцию для перезаписи файлов памяти проекта в .gemini/memory/"

const fs = require('fs');
const path = require('path');

function update_memory(category, new_content) {
    const validCategories = ['architecture', 'lore', 'current_state'];
    
    if (!validCategories.includes(category)) {
        return "Ошибка: неверная категория памяти.";
    }

    const filePath = path.join(__dirname, '..', 'memory', `${category}.md`);
    
    try {
        fs.writeFileSync(filePath, new_content, 'utf8');
        return `Файл ${category}.md успешно обновлен.`;
    } catch (err) {
        return `Ошибка записи: ${err.message}`;
    }
}

module.exports = { update_memory }; 
