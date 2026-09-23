package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubstitutionRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var repository: HttpTimetableRepository

    @Before fun setUp() {
        server.start()
        val factory = ApiFactory()
        repository = HttpTimetableRepository(
            FakeSettings(serverUrl = server.url("/").toString(), token = "teacher-token"),
            factory, OfflineCache(folder.newFolder(), factory.json),
        )
    }

    @After fun tearDown() { server.shutdown() }

    private fun requestJson(status: String) = """{"id":1,"teacherId":3,"lessonId":7,"date":"2026-09-24","classShort":"8b","subjectShort":"Ma","position":3,"originalTeacherShort":"MEY","status":"$status"}"""

    @Test fun `loads only open requests with session token`() = runTest {
        server.enqueue(MockResponse().setBody("""[${requestJson("UNKNOWN")}]"""))
        val result = repository.loadSubstitutionRequests() as SubstitutionResult.Success
        val entry = result.value.single()
        assertEquals(7L, entry.lessonId)
        assertEquals("2026-09-24", entry.date)
        assertEquals("8b", entry.classShort)
        assertEquals("Ma", entry.subjectShort)
        assertEquals(3, entry.position)
        assertEquals("MEY", entry.originalTeacherShort)
        assertNull(entry.roomShort)
        val request = server.takeRequest()
        assertEquals("/api/substitution-requests/me?open=true", request.path)
        assertEquals("Bearer teacher-token", request.getHeader("Authorization"))
    }

    @Test fun `both answers use PATCH and contract status`() = runTest {
        for (status in listOf(SubstitutionRequestStatus.ACCEPTED, SubstitutionRequestStatus.DECLINED)) {
            server.enqueue(MockResponse().setBody(requestJson(status.name)))
            val result = repository.answerSubstitutionRequest(1, status) as SubstitutionResult.Success
            assertEquals(status, result.value.status)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/substitution-requests/me/1", request.path)
            assertEquals("Bearer teacher-token", request.getHeader("Authorization"))
            assertEquals("""{"status":"${status.name}"}""", request.body.readUtf8())
        }
    }

    @Test fun `failed answers preserve HTTP status for UI handling`() = runTest {
        for (code in listOf(401, 403, 404, 409, 500)) {
            server.enqueue(MockResponse().setResponseCode(code))
            val result = repository.answerSubstitutionRequest(1, SubstitutionRequestStatus.ACCEPTED)
            assertEquals(code, (result as SubstitutionResult.Failure).code)
        }
    }
}
