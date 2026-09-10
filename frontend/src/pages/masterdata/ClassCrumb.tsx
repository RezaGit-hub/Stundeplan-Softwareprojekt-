// Dritte Brotkrume der Klassendetailseite: „Klasse 7a" statt „Klasse 1" — liest die Klasse aus dem Query-Cache.
import { useClass } from '@/api/hooks'

export function ClassCrumb({ id }: { id?: string }) {
  const classId = id && /^\d+$/.test(id) ? Number(id) : null
  const c = useClass(classId)
  return <>Klasse {c.data?.name ?? '…'}</>
}
