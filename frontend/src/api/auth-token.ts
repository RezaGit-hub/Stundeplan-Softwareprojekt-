// Das Token dieser Sitzung, im Speicher — der AuthProvider füllt es, der Client (und die Attrappe) lesen
// es; so braucht src/api keinen React-Kontext.
//
// Achtung, seit F-5a (05.09.): dieselbe Zeichenkette liegt ZUSÄTZLICH im localStorage
// (`src/api/session.ts`, Schlüssel `stundenplan.session`), damit ein Neuladen angemeldet bleibt. Der
// frühere Satz „nie localStorage" galt nur bis zum 04.09. und stand hier noch, als der Code schon
// anders war (Befund SICHERHEIT-3, 05.09.).
let token: string | null = null

export const authToken = {
  get: (): string | null => token,
  set: (t: string): void => {
    token = t
  },
  clear: (): void => {
    token = null
  },
}
