import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import { exec } from 'node:child_process';
import path from 'node:path';

// Кастомный плагин для открытия папки docs в проводнике Windows
const openDocsPlugin = () => ({
  name: 'open-docs-plugin',
  configureServer(server) {
    server.middlewares.use((req, res, next) => {
      if (req.url === '/api/open-docs') {
        const docsPath = path.resolve(__dirname, '../docs/academy');
        // В Windows надежнее использовать cmd /c start для беспрепятственного открытия папки
        const command = process.platform === 'win32'
          ? `cmd /c start "" "${docsPath}"`
          : `open "${docsPath}"`;
        exec(command, (err) => {
          if (err) {
            console.error('Ошибка открытия папки:', err);
            res.statusCode = 500;
            res.end(JSON.stringify({ error: err.message }));
          } else {
            res.statusCode = 200;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ success: true }));
          }
        });
      } else {
        next();
      }
    });
  }
});

export default defineConfig({
  plugins: [vue(), openDocsPlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000
  }
});
