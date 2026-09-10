// „Zur Stunde springen" aus der Konfliktliste: Hervorhebung setzen, hinscrollen, nach einer Sekunde
// wieder ausblenden — und, wenn die Stunde in der gewählten Sicht gar nicht steht, vorher auf die
// Schulsicht wechseln. Entwurfs- und Planseite hatten dafür bis zum 05.09. denselben Block doppelt
// (Befund FRONTEND-8); der 300-ms-Aufschub ist die Zeit, die das Raster zum Neuzeichnen braucht.
import { useState } from 'react'

const HIGHLIGHT_MS = 1000
const RERENDER_MS = 300

export function useJumpToLesson(isVisible: (lessonId: number) => boolean, showAll: () => void) {
  const [highlight, setHighlight] = useState<number | null>(null)

  const jump = (lessonId: number) => {
    const visible = isVisible(lessonId)
    if (!visible) showAll()
    setHighlight(lessonId)
    const delay = visible ? 0 : RERENDER_MS
    window.setTimeout(() => {
      document
        .querySelector<HTMLElement>(`[data-lesson-id="${lessonId}"]`)
        ?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    }, delay)
    window.setTimeout(() => setHighlight((h) => (h === lessonId ? null : h)), HIGHLIGHT_MS + delay)
  }

  return { highlight, jump }
}
