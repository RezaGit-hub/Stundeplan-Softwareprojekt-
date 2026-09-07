// Einzige Stelle für Bewegungszeiten und -kurven. Bei prefers-reduced-motion schaltet index.css alles ab.
export const EASE: [number, number, number, number] = [0.16, 1, 0.3, 1]
export const FAST = 0.15
export const BASE = 0.2
export const SLOW = 0.25

/** Seitenwechsel (AnimatedOutlet): Opacity + 6 px */
export const pageTransition = {
  initial: { opacity: 0, y: 6 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -6 },
  transition: { duration: BASE, ease: EASE },
}

/** Listenzeilen: 20 ms Versatz je Zeile, gedeckelt bei 8 */
export function listItem(i: number) {
  return {
    initial: { opacity: 0, y: 4 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: FAST, ease: EASE, delay: Math.min(i, 8) * 0.02 },
  }
}

/** Login-Karte fährt 8 px hoch ein */
export const cardEnter = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  transition: { duration: BASE, ease: EASE },
}

/** Kachel-Hover (LessonTile) */
export const tileHover = { y: -1, transition: { duration: FAST, ease: EASE } }
