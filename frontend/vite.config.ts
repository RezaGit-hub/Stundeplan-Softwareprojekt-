import path from 'node:path'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig, loadEnv, type UserConfig } from 'vite'

// Die Basis im Client ist immer relativ "/api" — es gibt keine URL im Build.
// Wohin der Vite-Proxy /api weiterreicht, entscheidet VITE_PROXY_TARGET aus der .env-Datei des Modus:
//   npm run dev        -> keine Datei      -> http://localhost:8080  (Spring-Boot-Backend)
//   npm run dev:prism  -> .env.prism       -> http://localhost:1501  (Prism-Mock aus api-contract/)
//   npm run dev:mock   -> .env.mock        -> Attrappe im Browser, der Proxy wird gar nicht benutzt
// baseConfig wird auch von vitest.config.ts und vite.demo.config.ts benutzt (mergeConfig braucht ein Objekt).
export function baseConfig(mode: string): UserConfig {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react(), tailwindcss()],
    resolve: { alias: { '@': path.resolve(import.meta.dirname, './src') } },
    server: { proxy: { '/api': env.VITE_PROXY_TARGET || 'http://localhost:8080' } },
  }
}

export default defineConfig(({ mode }) => baseConfig(mode))
