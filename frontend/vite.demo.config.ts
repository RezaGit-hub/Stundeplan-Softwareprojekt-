import { defineConfig, mergeConfig } from 'vite'
import { viteSingleFile } from 'vite-plugin-singlefile'
import { baseConfig } from './vite.config'

export default defineConfig(({ mode }) => mergeConfig(baseConfig(mode), {
  base: './',
  plugins: [viteSingleFile()],
  build: { outDir: '.demo-dist', emptyOutDir: true },
}))
