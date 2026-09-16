// Wettlauf zwischen Solver-Polling und Entwurfs-Mutation (npm test).
//
// useDraft lädt während SOLVING alle 2 s neu; jede Entwurfs-Mutation schreibt die Antwort direkt mit
// setQueryData(['draft', id]). Startet ein GET /drafts/{id} VOR der Mutation und trifft seine Antwort DANACH ein,
// überschreibt der alte Stand den neuen — verschobene Stunden, Pins und Handaufbau springen zurück.
// Deshalb bricht useApiMutation die Abfragen aus MutationOpts.cancel in onMutate ab (cancelQueryKeys).
// Der Test prüft beide Richtungen: ohne Abbruch gewinnt die alte Antwort, mit Abbruch die neue.
// Seit #103 trägt der Schlüssel die Kennung des Entwurfs — der Wettlauf läuft je Entwurf einzeln.
import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { cancelQueryKeys } from './hooks'
import { qk } from './query-keys'

type Draft = { id: number; stand: string }

const ALT: Draft = { id: 1, stand: 'alt' }
const NEU: Draft = { id: 1, stand: 'neu (Antwort der Mutation)' }

function client() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

/** Ein laufendes GET /draft, dessen Antwort erst auf Zuruf eintrifft. */
function langsamesGet(qc: QueryClient) {
  let antworten: (d: Draft) => void = () => {}
  const antwort = new Promise<Draft>((resolve) => { antworten = resolve })
  const laeuft = qc.fetchQuery({ queryKey: qk.draft(1), queryFn: () => antwort }).catch(() => {})
  return { antworten: (d: Draft) => antworten(d), fertig: laeuft }
}

describe('Entwurf: Polling gegen Mutation', () => {
  it('ohne Abbruch überschreibt die späte GET-Antwort das Mutationsergebnis', async () => {
    const qc = client()
    const get = langsamesGet(qc)

    qc.setQueryData(qk.draft(1), NEU) // Mutation ist durch
    get.antworten(ALT) // erst jetzt trifft das vorher gestartete GET ein
    await get.fertig

    expect(qc.getQueryData<Draft>(qk.draft(1))).toEqual(ALT) // der Fehler, den der Abbruch verhindert
  })

  it('mit cancelQueryKeys bleibt das Mutationsergebnis stehen', async () => {
    const qc = client()
    const get = langsamesGet(qc)

    await cancelQueryKeys(qc, [qk.draft(1)]) // das macht useApiMutation in onMutate
    qc.setQueryData(qk.draft(1), NEU)
    get.antworten(ALT)
    await get.fertig

    expect(qc.getQueryData<Draft>(qk.draft(1))).toEqual(NEU)
  })
})
