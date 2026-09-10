// Eine Schnittstelle, zwei Implementierungen. VITE_MOCK=true → Attrappe, sonst echter Client gegen /api.
//
// Die Attrappe wird bewusst NICHT statisch importiert (Befund FRONTEND-3, 05.09.): ein statischer Import
// zieht sie samt Demo-Konten im Klartext und 676 Zeilen Ferienvorlagen in JEDEN Build — gemessen 220 kB
// im Produktionsbundle, obwohl `IS_MOCK` dort false ist und nichts davon je aufgerufen wird. `main.tsx`
// laedt sie nur im Attrappen-Modus nach und meldet sie hier an; ohne das bleibt `httpApi` stehen.
import type { Api } from './types'
import { httpApi } from './client'

export const IS_MOCK = import.meta.env.VITE_MOCK === 'true'

// `let` mit Export: ESM-Bindungen sind lebendig, alle Module sehen den getauschten Wert.
export let api: Api = httpApi

/** Nur fuer den Attrappen-Modus (main.tsx) und Tests — im Betrieb bleibt es beim echten Client. */
export function setApi(next: Api): void {
  api = next
}
