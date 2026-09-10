# Beispielseiten des Vite-Gerüsts (Team, 03.09.2026)

Herkunft: das erste `frontend/`-Gerüst des Teams (`App.jsx`, `main.jsx`, `components/Navbar.jsx`, `pages/{Home,ClientList,ClientEdit}.jsx`,
`App.css`, `assets/`). Unverändert übernommen, nur der Ordner ist neu — nichts vom Team ist verloren gegangen.

Nicht geroutet: `src/app/router.tsx` kennt diese Seiten nicht, `vite build` bündelt sie nicht, `eslint.config.js` ignoriert
`src/example/**`, `tsconfig.app.json` prüft sie nicht (`checkJs: false`).

Gegenstück im Backend: `backend/src/main/java/de/wsp3_1/example/` beantwortet `GET /api/clients`. Beides ist Lesestoff für den
Vergleich „Gerüst gegen Schablone" und wird vor der Liveschaltung am 20.09. entfernt.
