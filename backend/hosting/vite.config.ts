import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { defineConfig } from 'vite';

const root = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig({
  root,
  publicDir: resolve(root, 'static'),
  build: {
    outDir: resolve(root, 'public'),
    emptyOutDir: true,
    sourcemap: false,
    target: 'es2022',
    rollupOptions: {
      input: {
        home: resolve(root, 'index.html'),
        deleteAccount: resolve(root, 'delete/index.html'),
        privacy: resolve(root, 'privacy/index.html'),
        terms: resolve(root, 'terms/index.html'),
        support: resolve(root, 'support/index.html'),
        notFound: resolve(root, '404.html'),
      },
    },
  },
});
