// Seiten werden erst beim Aufruf geladen. Ohne das steckt jede der 20 Seiten samt ihrer Bibliotheken
// im ersten Bündel — der Kalender (react-day-picker + date-fns) hängt allein am Wochenwähler, das
// Ziehen und Ablegen (@dnd-kit) allein am Entwurf, und beides wartet auf der Anmeldemaske mit
// (Befund FRONTEND-4, 05.09.).
//
// `lazyPage` nimmt der Aufrufstelle die immer gleiche Übersetzung ab: die Seiten sind benannte
// Exporte, React.lazy will einen Standardexport.
import { lazy, type ComponentType } from 'react'

export function lazyPage<K extends string>(
  load: () => Promise<Record<K, ComponentType<Record<string, never>>>>,
  name: K,
) {
  return lazy(() => load().then((module) => ({ default: module[name] })))
}
