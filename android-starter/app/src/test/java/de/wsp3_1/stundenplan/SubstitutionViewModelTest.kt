package de.wsp3_1.stundenplan

import de.wsp3_1.stundenplan.data.*
import de.wsp3_1.stundenplan.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubstitutionViewModelTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val request = SubstitutionRequestDto(1, 3, 7, SubstitutionRequestStatus.UNKNOWN)

    @Test fun `teacher can answer and confirmed request disappears`() = runTest {
        val settings = FakeSettings(serverUrl = "http://localhost", token = "t", role = "TEACHER")
        val repository = object : TimetableRepository by FakeRepository(settings) {
            override suspend fun loadSubstitutionRequests() = SubstitutionResult.Success(listOf(request))
            override suspend fun answerSubstitutionRequest(id: Long, status: SubstitutionRequestStatus) =
                SubstitutionResult.Success(request.copy(status = status))
        }
        val vm = TimetableViewModel(settings, repository, FakeThemePreferences())
        vm.openSubstitute()
        advanceUntilIdle()
        assertEquals(Screen.Substitute, vm.screen.value)
        assertEquals(listOf(request), vm.substitutionState.value.requests)
        vm.answerSubstitution(1, SubstitutionRequestStatus.ACCEPTED)
        advanceUntilIdle()
        assertTrue(vm.substitutionState.value.requests.isEmpty())
        assertEquals("Anfrage angenommen.", vm.substitutionState.value.notice)
    }

    @Test fun `failed answer keeps request visible and allows retry`() = runTest {
        val settings = FakeSettings(serverUrl = "http://localhost", token = "t", role = "TEACHER")
        val repository = object : TimetableRepository by FakeRepository(settings) {
            override suspend fun loadSubstitutionRequests() = SubstitutionResult.Success(listOf(request))
            override suspend fun answerSubstitutionRequest(id: Long, status: SubstitutionRequestStatus) =
                SubstitutionResult.Failure(409, "Bereits beantwortet")
        }
        val vm = TimetableViewModel(settings, repository, FakeThemePreferences())
        vm.openSubstitute()
        advanceUntilIdle()
        vm.answerSubstitution(1, SubstitutionRequestStatus.DECLINED)
        advanceUntilIdle()
        assertEquals(listOf(request), vm.substitutionState.value.requests)
        assertNull(vm.substitutionState.value.answeringId)
        assertEquals("Bereits beantwortet", vm.substitutionState.value.error)
    }

    @Test fun `student cannot open requests`() = runTest {
        val settings = FakeSettings(token = "t", role = "STUDENT")
        val vm = TimetableViewModel(settings, FakeRepository(settings), FakeThemePreferences())
        vm.openSubstitute()
        advanceUntilIdle()
        assertTrue(vm.screen.value is Screen.Day)
    }
}
