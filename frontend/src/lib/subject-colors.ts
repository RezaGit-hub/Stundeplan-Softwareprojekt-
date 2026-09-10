// Die 12 Fachfarben aus ui.md — identisch mit --subject-1..12 in index.css und Theme.kt der App.
export const SUBJECT_COLORS = [
  '#1976D2', '#388E3C', '#F57C00', '#7B1FA2', '#0097A7', '#C2185B',
  '#5D4037', '#455A64', '#AFB42B', '#E64A19', '#00796B', '#303F9F',
] as const

export function subjectColorByIndex(i: number): string {
  return SUBJECT_COLORS[((i % SUBJECT_COLORS.length) + SUBJECT_COLORS.length) % SUBJECT_COLORS.length]
}
