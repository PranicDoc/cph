/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig, loadEnv} from 'vite';

export default defineConfig(({mode}) => {
  const env = loadEnv(mode, '.', '');
  return {
    plugins: [react(), tailwindcss()],
    define: {
      'process.env.GEMINI_API_KEY': JSON.stringify(env.GEMINI_API_KEY),
      'process.env.GOOGLE_CLIENT_ID': JSON.stringify(env.GOOGLE_CLIENT_ID),
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    build: {
      rollupOptions: {
        output: {
          // Libraries change far less often than app code: separate files stay cached across deploys.
          // Chart, markdown and date-picker libraries only load with the pages that use them.
          manualChunks(id) {
            const pkg = id.match(/node_modules\/((?:@[^/]+\/)?[^/]+)/)?.[1];
            if (!pkg) return undefined;
            if (/^(react|react-dom|scheduler|react-router|react-router-dom)$/.test(pkg)) return 'vendor-react';
            if (pkg.startsWith('@tanstack/')) return 'vendor-tanstack';
            if (/^(recharts|d3-.+|victory-vendor|@reduxjs\/.+|redux|immer|reselect|decimal\.js-light|es-toolkit)$/.test(pkg)) return 'vendor-charts';
            if (/^(react-markdown|remark-.+|micromark.*|mdast-.+|hast-.+|unist-.+|unified|vfile.*)$/.test(pkg)) return 'vendor-markdown';
            if (/^(date-fns|react-day-picker)$/.test(pkg)) return 'vendor-dates';
            return 'vendor';
          },
        },
      },
    },
    server: {
      hmr: process.env.DISABLE_HMR !== 'true',
      proxy: {
        '/cph': {
          target: 'http://localhost:9918',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      // The popover/combobox tests drive many user events; with every file running in parallel
      // they can pass 5s on a busy machine or CI runner and fail for no real reason.
      testTimeout: 15000,
    },
  };
});
