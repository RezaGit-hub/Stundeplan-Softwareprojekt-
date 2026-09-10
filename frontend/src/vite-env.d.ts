/// <reference types="vite/client" />

// Alle Umgebungsvariablen der Web-App. Werte sind immer Strings (Vite). Siehe .env.example.
interface ImportMetaEnv {
  /** 'true' = In-Memory-Attrappe statt /api (npm run dev:mock, build:mock, build:demo) */
  readonly VITE_MOCK?: string
  /** 'true' = createHashRouter (nur Ein-Datei-Demo) */
  readonly VITE_HASH_ROUTER?: string
  /** 'SMALL' (Standard) | 'FULL' */
  readonly VITE_MOCK_DATASET?: string
  /** festes Heute, yyyy-MM-dd */
  readonly VITE_MOCK_TODAY?: string
  /** Kunstlatenz in ms; leer = 150–400 ms zufällig */
  readonly VITE_MOCK_LATENCY?: string
  /** 'leitszenario' = Entwurf + Ereignisse + Vertretung beim Start vorlegen */
  readonly VITE_MOCK_SCENARIO?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
