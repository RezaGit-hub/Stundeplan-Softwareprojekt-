// Kunstlatenz, damit Lade- und Wartezustände sichtbar werden. VITE_MOCK_LATENCY=0 schaltet sie ab (Screenshots, Tests).
const fixed = import.meta.env.VITE_MOCK_LATENCY

export function latency(): Promise<void> {
  const ms = fixed !== undefined && fixed !== '' ? Number(fixed) : 150 + Math.random() * 250
  if (!(ms > 0)) return Promise.resolve()
  return new Promise((resolve) => setTimeout(resolve, ms))
}
