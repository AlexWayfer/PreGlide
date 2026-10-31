package name.alexwayfer.preglide.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DiagnosticEventsTest {
    @Test
    fun glideTotalsHaveTheirOwnSectionAndEmptyTotalsAddNothing() {
        assertEquals("", glideStatisticsReport(emptyList()))
        assertEquals(
            "\nGlide statistics (all recorded use):\n  kept 100\n  erased 3\n",
            glideStatisticsReport(listOf("kept 100", "erased 3")),
        )
    }

    private val minute = 60_000L
    private val pixel = DiagnosticDevice(
        "0.1.0 (1)",
        "15",
        35,
        "Google",
        "Pixel 8",
        languages = listOf("ru-RU", "en-US"),
        debug = false,
        installedAtMillis = minute,
    )

    private fun failure(detail: String, at: Long, message: String = "") = DiagnosticEvent(
        kind = DiagnosticKind.Failure,
        source = "Prediction",
        detail = detail,
        message = message,
        firstAtMillis = at,
    )

    @Test
    fun aRepeatRaisesTheCountAndKeepsTheFirstTime() {
        val first = recordDiagnosticEvent(emptyList(), failure("LiteRtException", 1_000, "old"))
        val events = recordDiagnosticEvent(first, failure("LiteRtException", 5_000, "new"))

        assertEquals(1, events.size)
        assertEquals(2, events[0].count)
        assertEquals(1_000L, events[0].firstAtMillis)
        assertEquals(5_000L, events[0].lastAtMillis)
        assertEquals("new", events[0].message)
    }

    @Test
    fun anotherExceptionIsAnotherProblemAndTheNewestComesFirst() {
        val events = recordDiagnosticEvent(listOf(failure("A", 1_000)), failure("B", 2_000))

        assertEquals(listOf("B", "A"), events.map { it.detail })
    }

    @Test
    fun theOldestProblemFallsOffPastTheLimit() {
        val full = (1..DIAGNOSTIC_JOURNAL_LIMIT).fold(emptyList<DiagnosticEvent>()) { events, index ->
            recordDiagnosticEvent(events, failure("E$index", index.toLong()))
        }
        val events = recordDiagnosticEvent(full, failure("Newest", 1_000))

        assertEquals(DIAGNOSTIC_JOURNAL_LIMIT, events.size)
        assertEquals("Newest", events.first().detail)
        assertFalse(events.any { it.detail == "E1" })
    }

    @Test
    fun aNewProblemIsNoticedAndItsRepeatIsNot() {
        val events = listOf(failure("A", 1_000))

        assertTrue(diagnosticNoticeDue(events, failure("B", 2_000), sentAtMillis = 0))
        assertFalse(diagnosticNoticeDue(events, failure("A", 2_000), sentAtMillis = 0))
    }

    @Test
    fun aRepeatOfAProblemAlreadySentIsNoticedAgain() {
        assertTrue(diagnosticNoticeDue(listOf(failure("A", 1_000)), failure("A", 3_000), sentAtMillis = 2_000))
    }

    @Test
    fun theNextReportHoldsOnlyWhatHappenedSinceTheLastOne() {
        val old = failure("Old", 1_000)
        val new = failure("New", 3_000)

        assertEquals(listOf(new), diagnosticEventsToSend(listOf(new, old), sentAtMillis = 2_000))
    }

    @Test
    fun withNothingNewSinceTheLastReportItHoldsTheWholeJournalAgain() {
        val events = listOf(failure("A", 1_000))

        assertEquals(events, diagnosticEventsToSend(events, sentAtMillis = 2_000))
    }

    @Test
    fun onlyCrashesAndFreezesAfterTheLastNoticeAreUnseen() {
        val crash = DiagnosticEvent(DiagnosticKind.Crash, "main", "IllegalStateException", firstAtMillis = 5_000)
        val oldFreeze = DiagnosticEvent(DiagnosticKind.Anr, "main", "frame", firstAtMillis = 1_000)
        val events = listOf(crash, oldFreeze, failure("A", 6_000))

        assertEquals(listOf(crash), unseenExitEvents(events, noticedAtMillis = 2_000))
    }

    @Test
    fun anrTraceKeepsOnlyTheMainThread() {
        val trace = """
            ----- pid 123 at 2026-10-01 -----
            "Signal Catcher" daemon prio=10 tid=2 Runnable
              at dalvik.Other.run(Other.java:1)

            "main" prio=5 tid=1 Blocked
              native: #00 pc 000 libc.so
              at name.alexwayfer.preglide.Slow.run(Slow.kt:10)
              at android.os.Handler.dispatchMessage(Handler.java:99)

            "worker" prio=5 tid=3 Waiting
        """.trimIndent()

        val main = anrMainThread(trace.lineSequence())

        assertEquals(
            listOf(
                "\"main\" prio=5 tid=1 Blocked",
                "  native: #00 pc 000 libc.so",
                "  at name.alexwayfer.preglide.Slow.run(Slow.kt:10)",
                "  at android.os.Handler.dispatchMessage(Handler.java:99)",
            ),
            main.lines(),
        )
        assertEquals("name.alexwayfer.preglide.Slow.run(Slow.kt:10)", topStackFrame(main))
    }

    @Test
    fun anrTraceWithoutAMainThreadKeepsNothing() {
        assertEquals("", anrMainThread("\"worker\" prio=5\n  at a.b(c)".lineSequence()))
        assertNull(topStackFrame(""))
    }

    @Test
    fun aCrashIsNamedByItsExceptionAndTopFrameAndKeepsTheStack() {
        val error = IllegalStateException("broken")
        val event = crashEvent(Thread("worker"), error, atMillis = 5)

        assertEquals(DiagnosticKind.Crash, event.kind)
        assertEquals("worker", event.source)
        assertEquals("java.lang.IllegalStateException at ${error.stackTrace.first()}", event.detail)
        assertTrue(event.message.startsWith("java.lang.IllegalStateException: broken"))
        assertEquals(5L, event.firstAtMillis)
    }

    @Test
    fun aFailureSaysWhatTheAppWasDoing() {
        val event = failureEvent("Prediction", "The model does not run", IllegalArgumentException(), atMillis = 7)

        assertEquals(DiagnosticKind.Failure, event.kind)
        assertEquals("Prediction", event.source)
        assertTrue(event.message.startsWith("The model does not run\njava.lang.IllegalArgumentException"))
        assertEquals("Prediction: ${event.detail}", diagnosticHeadline(event))
    }

    @Test
    fun anExceptionWithoutAStackIsNamedByItsClass() {
        val error = RuntimeException().apply { stackTrace = emptyArray() }

        assertEquals("java.lang.RuntimeException", failureEvent("S", "doing", error, 0).detail)
    }

    @Test
    fun reportNamesTheBuildTheDeviceAndEachProblem() {
        val repeated = failure("LiteRtException", 0, "The model does not run")
            .copy(lastAtMillis = 2 * minute, count = 3)

        val report = diagnosticReport(device = pixel, events = listOf(repeated), zone = ZoneOffset.UTC)

        assertEquals(
            """
            PreGlide problem report
            App: 0.1.0 (1)
            Installed: 1970-01-01T00:01:00Z
            Android: 15 (API 35), Google Pixel 8
            Languages: ru-RU, en-US

            Prediction: LiteRtException
            1970-01-01T00:02:00Z, 3 times since 1970-01-01T00:00:00Z
            The model does not run

            """.trimIndent(),
            report,
        )
    }

    @Test
    fun crashesAndFreezesHaveTheirOwnHeadlines() {
        val crash = DiagnosticEvent(DiagnosticKind.Crash, "main", "Boom at x", firstAtMillis = 0)
        val freeze = DiagnosticEvent(DiagnosticKind.Anr, "main", "a.b(c)", firstAtMillis = 0)

        assertEquals("Crash: Boom at x", diagnosticHeadline(crash))
        assertEquals("Freeze: a.b(c)", diagnosticHeadline(freeze))
    }

    @Test
    fun recentLogKeepsTheLatestLinesOldestFirst() {
        val log = RecentLog()
        (1..RECENT_LOG_LINES + 2).forEach { log.add("line $it") }

        val lines = log.lines()
        assertEquals(RECENT_LOG_LINES, lines.size)
        assertEquals("line 3", lines.first())
        assertEquals("line ${RECENT_LOG_LINES + 2}", lines.last())
    }

    @Test
    fun recentLogLineNamesTimeLevelAndTag() {
        assertEquals(
            "00:01:02.345 W/Prediction: model failed",
            recentLogLine(62_345, ZoneOffset.UTC, 'W', "Prediction", "model failed"),
        )
    }

    @Test
    fun aRepeatKeepsTheLogBeforeItsLatestTime() {
        val first = recordDiagnosticEvent(emptyList(), failure("A", 1_000).copy(recentLog = listOf("old")))
        val events = recordDiagnosticEvent(first, failure("A", 2_000).copy(recentLog = listOf("new")))

        assertEquals(listOf("new"), events.single().recentLog)
    }

    @Test
    fun reportListsTheLogUnderTheProblem() {
        val report = diagnosticReport(
            device = pixel,
            events = listOf(failure("A", 0, "failed").copy(recentLog = listOf("a", "b"))),
            zone = ZoneOffset.UTC,
        )

        assertTrue(report.endsWith("failed\nLog before it:\n  a\n  b\n"))
    }

    @Test
    fun reportFromTheAppScreenEndsWithTheAppLogEvenWithoutProblems() {
        val report = diagnosticReport(pixel, emptyList(), ZoneOffset.UTC, appLog = listOf("one", "two"))

        assertTrue(report.endsWith("Languages: ru-RU, en-US\n\nApp log:\n  one\n  two\n"))
    }

    @Test
    fun reportNamesTheTelegramAccountSoTheDeveloperCanAnswerOnlyWhenThereIsOne() {
        val named = diagnosticReport(pixel, emptyList(), ZoneOffset.UTC, telegramAccount = "@alex, ID 7")
        val unnamed = diagnosticReport(pixel, emptyList(), ZoneOffset.UTC)

        assertTrue(named.contains("Languages: ru-RU, en-US\nTelegram: @alex, ID 7\n"))
        assertFalse(unnamed.contains("Telegram:"))
    }

    @Test
    fun reportWithoutLanguagesOrInstallTimeSaysSo() {
        val report = diagnosticReport(
            device = pixel.copy(languages = emptyList(), installedAtMillis = null),
            events = emptyList(),
            zone = ZoneOffset.UTC,
        )

        assertFalse(report.contains("Installed:"))
        assertFalse(report.contains("App log:"))
        assertTrue(report.contains("Languages: -\n"))
    }

    @Test
    fun reportMarksADebugBuild() {
        val report = diagnosticReport(device = pixel.copy(debug = true), events = emptyList(), zone = ZoneOffset.UTC)

        assertTrue(report.contains("App: 0.1.0 (1) debug\nInstalled: "))
    }

    @Test
    fun shareTextNamesTheBuildAndCountsTheProblemsWithoutListingThem() {
        assertEquals("PreGlide 0.1.0 (1) debug\nProblems: 4", diagnosticShareText(pixel.copy(debug = true), 4))
        assertEquals("PreGlide 0.1.0 (1)", diagnosticShareText(pixel, 0))
    }

    @Test
    fun reportFileIsNamedByItsUtcTimeWithoutColons() {
        assertEquals("preglide-report_1970-01-01_00-02-05Z.txt", diagnosticReportFileName(2 * minute + 5_000))
    }

    @Test
    fun theLaterOfTheUpdateAndAStudioDeployIsTheInstallTime() {
        assertEquals(5_000L, diagnosticInstalledAt(packageUpdatedMillis = 1_000, studioOverlayMillis = 5_000))
        assertEquals(1_000L, diagnosticInstalledAt(packageUpdatedMillis = 1_000, studioOverlayMillis = 0))
        assertEquals(5_000L, diagnosticInstalledAt(packageUpdatedMillis = 5_000, studioOverlayMillis = 1_000))
        assertNull(diagnosticInstalledAt(packageUpdatedMillis = null, studioOverlayMillis = 0))
    }

    @Test
    fun journalSurvivesEncodingAndSkipsUnknownKinds() {
        val events = listOf(
            failure("A", 1_000, "failed").copy(lastAtMillis = 3_000, count = 2, recentLog = listOf("a")),
            DiagnosticEvent(DiagnosticKind.Crash, "main", "Boom", message = "stack", firstAtMillis = 500),
        )

        assertEquals(events, decodeDiagnosticEvents(encodeDiagnosticEvents(events)))
        assertEquals(emptyList<DiagnosticEvent>(), decodeDiagnosticEvents("""[{"kind":"Other"}]"""))
        assertEquals(emptyList<DiagnosticEvent>(), decodeDiagnosticEvents("not json"))
    }
}
