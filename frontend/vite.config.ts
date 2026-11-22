import { defineConfig } from 'vitest/config'

const rutaDesignSystem = decodeURIComponent(new URL('../design-system/', import.meta.url).pathname)
  .replace(/^\/([A-Za-z]:)/, '$1')
  .replace(/\/$/, '')

export default defineConfig({
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
    fs: {
      allow: [rutaDesignSystem],
    },
  },
  resolve: {
    alias: {
      '@design-system': rutaDesignSystem,
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
