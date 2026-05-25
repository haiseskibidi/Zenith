const fs = require('fs');
const path = require('path');

/**
 * Безопасно добавляет имя ресурса в .index файл (Append-only).
 * Предотвращает дублирование и случайную перезапись.
 * 
 * @param {string} indexPath - Путь к .index файлу относительно корня проекта.
 * @param {string} resourceName - Имя JSON файла ресурса (например, "my_block.json").
 */
function add_to_index(indexPath, resourceName) {
    const fullPath = path.resolve(process.cwd(), indexPath);
    
    try {
        if (!fs.existsSync(fullPath)) {
            fs.writeFileSync(fullPath, resourceName + '\n', 'utf8');
            return `Файл ${indexPath} создан, ресурс ${resourceName} добавлен.`;
        }

        const content = fs.readFileSync(fullPath, 'utf8');
        const lines = content.split(/\r?\n/).map(line => line.trim()).filter(line => line.length > 0);

        if (lines.includes(resourceName)) {
            return `Ресурс ${resourceName} уже присутствует в ${indexPath}. Пропускаю.`;
        }

        const separator = content.endsWith('\n') || content.length === 0 ? '' : '\n';
        fs.appendFileSync(fullPath, separator + resourceName + '\n', 'utf8');
        
        return `Ресурс ${resourceName} успешно добавлен в ${indexPath}.`;
    } catch (err) {
        return `Ошибка при работе с индексом ${indexPath}: ${err.message}`;
    }
}

// Позволяет запускать скрипт напрямую из CLI: node add_resource.js <path> <resource>
if (require.main === module) {
    const args = process.argv.slice(2);
    if (args.length < 2) {
        console.error('Usage: node add_resource.js <indexPath> <resourceName>');
        process.exit(1);
    }
    console.log(add_to_index(args[0], args[1]));
}

module.exports = { add_to_index };
