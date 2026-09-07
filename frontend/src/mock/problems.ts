// Fabriken für ProblemDetail-Fehler der Attrappe (RFC 9457, wie der Spring-@RestControllerAdvice).
import { ApiError, type ConflictDto, type FieldErrorDto } from '@/api/types'

const T = 'urn:pushnpull:problem:'

export function validation(errors: FieldErrorDto[]): ApiError {
  return new ApiError({ type: `${T}validation`, title: 'Eingaben unvollständig', status: 400, detail: 'Bitte die markierten Felder prüfen.', errors })
}

export function notFound(detail: string): ApiError {
  return new ApiError({ type: `${T}not-found`, title: 'Nicht gefunden', status: 404, detail })
}

export function conflict409(
  suffix: 'in-use' | 'draft-exists' | 'solving' | 'master-data-incomplete' | 'pinned-contradiction' | 'data-exists' | 'exception-exists' | 'preset-missing',
  title: string,
  detail: string,
  extra?: { missing?: string[]; conflicts?: ConflictDto[] },
): ApiError {
  return new ApiError({ type: `${T}${suffix}`, title, status: 409, detail, ...extra })
}

export function forbidden(): ApiError {
  return new ApiError({ type: `${T}forbidden`, title: 'Kein Zugriff', status: 403, detail: 'Diese Aktion ist der Rolle Planung vorbehalten.' })
}

// Standardtext = der Anmeldefall; das Backend schickt hier denselben Satz (ApiProblems.badCredentials).
export function unauthenticated(detail = 'Benutzername oder Passwort ist falsch.'): ApiError {
  return new ApiError({ type: `${T}unauthenticated`, title: 'Nicht angemeldet', status: 401, detail })
}
