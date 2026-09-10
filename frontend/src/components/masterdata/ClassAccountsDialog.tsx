// „Konten für alle Klassen anlegen“ (E-50): ein Klassenkonto je aktiver Klasse ohne Konto.
//
// Der Dialog hat zwei Zustände. Vorher erklärt er, was passiert und warum ein Konto je Klasse die
// datensparsame Lösung ist. Nachher zeigt er die Zugangsdaten als Tabelle — **einmal**: der Server gibt
// die Passwörter nur in dieser einen Antwort im Klartext heraus, danach kennt er nur den Hash. Wer eines
// verliert, setzt in der Kontenliste über „Passwort setzen“ ein neues.
//
// Kopieren geht über die Zwischenablage (Text mit Tabulatoren — passt in Tabellenkalkulation und Serienbrief),
// Drucken über das Druckbild des Browsers. Beides bleibt im Browser; die Passwörter wandern nirgendwo hin.
import { useState } from 'react'
import { toast } from 'sonner'
import { Copy, Printer, ShieldCheck, Users } from 'lucide-react'
import { useCreateClassAccounts } from '@/api/hooks'
import type { ClassAccountDto } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

/** Der Datenschutztext, den die Planung beim Verteilen weitergibt (Recherche „Bewertung“, E-50). */
export const CLASS_ACCOUNT_NOTE =
  'Ein Konto je Klasse. Passwort in der Klasse weitergeben, halbjährlich ändern. '
  + 'Die Sicht zeigt nur die eigene Klasse, Lehrkräfte als Kürzel, keine Abwesenheitsgründe.'

function asText(rows: ClassAccountDto[]): string {
  return ['Klasse\tBenutzername\tPasswort', ...rows.map((r) => `${r.classShort}\t${r.username}\t${r.password}`)].join('\n')
}

export function ClassAccountsDialog({ open, onOpenChange }: { open: boolean; onOpenChange(open: boolean): void }) {
  const create = useCreateClassAccounts()
  const [rows, setRows] = useState<ClassAccountDto[] | null>(null)

  const run = async () => {
    try {
      const created = await create.mutateAsync()
      setRows(created)
      toast.success(created.length === 0 ? 'Jede aktive Klasse hat bereits ein Konto' : `${created.length} Klassenkonten angelegt`)
    } catch {
      // Der Fehler-Toast kommt aus der Mutation (errors.ts); der Dialog bleibt offen.
    }
  }

  const copy = async () => {
    if (!rows) return
    try {
      await navigator.clipboard.writeText(asText(rows))
      toast.success('Zugangsdaten in die Zwischenablage kopiert')
    } catch {
      toast.error('Kopieren nicht möglich — bitte die Tabelle von Hand markieren.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Konten für alle Klassen anlegen</DialogTitle>
          <DialogDescription>
            {rows
              ? 'Diese Zugangsdaten werden nur jetzt angezeigt. Bitte kopieren oder ausdrucken.'
              : 'Für jede aktive Klasse ohne Konto entsteht ein Konto. Bestehende Konten bleiben unverändert.'}
          </DialogDescription>
        </DialogHeader>

        {!rows && (
          <div className="space-y-3 py-1 text-sm">
            <p className="flex gap-2 rounded-md border bg-surface-sunken/60 p-3 text-foreground-muted">
              <ShieldCheck className="mt-0.5 size-4 shrink-0" aria-hidden />
              <span>{CLASS_ACCOUNT_NOTE}</span>
            </p>
            <p className="text-foreground-subtle">
              Benutzername ist das Klassenkürzel in Kleinbuchstaben (zum Beispiel „7a“). Das Passwort erzeugt der
              Server zufällig und zeigt es genau einmal an.
            </p>
          </div>
        )}

        {rows && rows.length === 0 && (
          <p className="py-2 text-sm text-foreground-muted">Jede aktive Klasse hat bereits ein Konto — es wurde nichts angelegt.</p>
        )}

        {rows && rows.length > 0 && (
          <div className="space-y-3">
            <div className="max-h-[45vh] overflow-auto rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Klasse</TableHead>
                    <TableHead>Benutzername</TableHead>
                    <TableHead>Passwort</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((r) => (
                    <TableRow key={r.username}>
                      <TableCell className="font-medium">{r.classShort}</TableCell>
                      <TableCell className="font-mono">{r.username}</TableCell>
                      <TableCell className="font-mono select-all">{r.password}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <p className="flex gap-2 rounded-md border bg-surface-sunken/60 p-3 text-xs text-foreground-muted">
              <ShieldCheck className="mt-0.5 size-4 shrink-0" aria-hidden />
              <span>{CLASS_ACCOUNT_NOTE}</span>
            </p>
          </div>
        )}

        <DialogFooter>
          {rows ? (
            <>
              <Button type="button" variant="outline" onClick={() => void copy()} disabled={rows.length === 0}><Copy /> Kopieren</Button>
              <Button type="button" variant="outline" onClick={() => window.print()} disabled={rows.length === 0}><Printer /> Drucken</Button>
              <Button type="button" onClick={() => onOpenChange(false)}>Fertig</Button>
            </>
          ) : (
            <>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={create.isPending}>Abbrechen</Button>
              <Button type="button" onClick={() => void run()} disabled={create.isPending}><Users /> Konten anlegen</Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
