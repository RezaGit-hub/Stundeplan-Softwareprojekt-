package de.wsp3_1.stundenplan.data

import java.time.LocalDate

/** TEMPORÄRE DEMODATEN: in AppContainer abschalten und diese Datei später löschen. */
class DemoSubstitutionRepository(
    private val delegate: TimetableRepository,
) : TimetableRepository by delegate {
    private val requests = mutableListOf(
        demoRequest(1, 1, "7a", "Mathematik", 2, "MEY"),
        demoRequest(2, 1, "8b", "Deutsch", 4, "SCH"),
        demoRequest(3, 2, "9a", "Physik", 3, "WEB"),
        demoRequest(4, 3, "6c", "Englisch", 1, "CLA"),
    )

    override suspend fun loadSubstitutionRequests(): SubstitutionResult<List<SubstitutionRequestDto>> =
        SubstitutionResult.Success(requests.toList())

    override suspend fun answerSubstitutionRequest(
        id: Long, status: SubstitutionRequestStatus,
    ): SubstitutionResult<SubstitutionRequestDto> {
        require(status != SubstitutionRequestStatus.UNKNOWN)
        val request = requests.firstOrNull { it.id == id }
            ?: return SubstitutionResult.Failure(404, "Demo-Anfrage bereits beantwortet.")
        requests.remove(request)
        return SubstitutionResult.Success(request.copy(status = status))
    }

    private fun demoRequest(id: Long, days: Long, schoolClass: String, subject: String, position: Int, teacher: String) =
        SubstitutionRequestDto(
            id = id, teacherId = 1, lessonId = id,
            date = LocalDate.now().plusDays(days).toString(),
            classShort = schoolClass, subjectShort = subject, position = position,
            originalTeacherShort = teacher, status = SubstitutionRequestStatus.UNKNOWN,
        )
}
