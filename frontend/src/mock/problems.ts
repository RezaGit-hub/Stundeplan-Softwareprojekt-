// Fabriken für ProblemDetail-Fehler der Attrappe (RFC 9457, wie der Spring-@RestControllerAdvice).
import { ApiError, type CapacityViolation, type ConflictDto, type FieldErrorDto } from '@/api/types'

const T = 'urn:pushnpull:problem:'

export function validation(errors: FieldErrorDto[]): ApiError {
  return new ApiError({ type: `${T}validation`, title: 'Eingaben unvollständig', status: 400, detail: 'Bitte die markierten Felder prüfen.', errors })
}

export function notFound(detail: string): ApiError {
  return new ApiError({ type: `${T}not-found`, title: 'Nicht gefunden', status: 404, detail })
}

export function conflict409(
  // `draft-exists` stand hier bis 1.3.0 (ein Entwurf je Schule); seit #103 stehen Entwürfe nebeneinander
  // und der Slug kommt nicht mehr vor.
  suffix: 'in-use' | 'solving' | 'master-data-incomplete' | 'pinned-contradiction' | 'solve-capacity-exceeded' | 'data-exists' | 'exception-exists' | 'preset-missing' | 'protected-account' | 'draft-has-conflicts' | 'plan-overlap' | 'draft-finished',
  title: string,
  detail: string,
  extra?: { missing?: string[]; conflicts?: ConflictDto[]; capacityViolations?: CapacityViolation[] },
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
