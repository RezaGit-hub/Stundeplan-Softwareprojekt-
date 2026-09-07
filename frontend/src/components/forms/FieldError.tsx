/** Feldfehler unter einem Input (aus useFieldErrors().errors[field]). Rendert nichts ohne Text. */
export function FieldError({ message, id }: { message?: string; id?: string }) {
  if (!message) return null
  return (
    <p id={id} role="alert" className="mt-1 text-xs text-destructive">
      {message}
    </p>
  )
}
