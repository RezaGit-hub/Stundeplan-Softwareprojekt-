// Kopiert die Ein-Datei-Demo (.demo-dist/index.html) nach ../demo/webapp-demo.html und meldet die Größe.
import { copyFileSync, mkdirSync, statSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const src = resolve(root, '.demo-dist/index.html')
const dst = resolve(root, '../demo/webapp-demo.html')
mkdirSync(dirname(dst), { recursive: true })
copyFileSync(src, dst)
console.log(`webapp-demo.html: ${(statSync(dst).size / 1024 / 1024).toFixed(2)} MB → ${dst}`)
