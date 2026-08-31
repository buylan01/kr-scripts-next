package com.krscripts.app.shell

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellEventSourceTest {

    @Test
    fun postAndReadShellEvent() = runTest {
        val eventSource = ShellEventSource()
        val events = mutableListOf<ShellEvent>()
        val progress = mutableListOf<Pair<Int, Int>?>()

        val job = launch {
            eventSource.events.collect { events.add(it) }
        }

        val jobProgress = launch {
            eventSource.progress.collect { progress.add(it) }
        }

        runCurrent()

        val forceStop = {}
        eventSource.postStart(forceStop)
        eventSource.postRead("progress:[10/100]")
        eventSource.postRead("OutputTest")
        eventSource.postReadError("ErrorTest")
        eventSource.postWrite("InputTest")
        eventSource.postExit("Exit")

        advanceUntilIdle()

        assertEquals(5, events.size)

        val started = events[0]
        assertNotNull((started as ShellEvent.Started).forceStop)

        val outputLog = events[1] as ShellEvent.Log
        assertEquals("OutputTest", outputLog.text)
        assertEquals(ShellLogType.OUTPUT, outputLog.type)

        val errorLog = events[2] as ShellEvent.Log
        assertEquals("ErrorTest", errorLog.text)
        assertEquals(ShellLogType.OUTPUT_ERROR, errorLog.type)

        val inputLog = events[3] as ShellEvent.Log
        assertEquals("InputTest", inputLog.text)
        assertEquals(ShellLogType.INPUT, inputLog.type)

        val exited = events[4] as ShellEvent.Exited
        assertEquals("Exit", exited.payload)

        assertEquals(10 to 100, progress.lastOrNull())

        eventSource.destroy()
        job.join()
        jobProgress.cancel()
    }
}