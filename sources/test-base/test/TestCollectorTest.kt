/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCollectorTest {
    @Test
    fun spans() = runTestWithCollector {
        spanBuilder("span1").setAttribute("key1", "value1").use {
            coroutineScope {
                launch(Dispatchers.IO) {
                    spanBuilder("span2").setAttribute("key2", "value2").use {
                        it.addEvent("x")
                    }
                }
            }
        }

        assertEquals(2, spans.size)
        val (span1, span2) = spans.sortedBy { it.name }

        assertEquals("span1", span1.name)
        assertEquals("key1=value1", span1.attributes.asMap().map { "${it.key}=${it.value}" }.joinToString(" "))

        assertEquals("span2", span2.name)
        assertEquals("key2=value2", span2.attributes.asMap().map { "${it.key}=${it.value}" }.joinToString(" "))

        assertEquals(span1.spanId, span2.parentSpanId)
    }

    @Test
    fun terminal() = runTestWithCollector {
        terminal.println("terminal stdout")
        terminal.println("terminal stderr", stderr = true)

        assertEquals("terminal stdout\n", terminalRecorder.stdout())
        assertEquals("terminal stderr\n", terminalRecorder.stderr())
    }
}
