package io.spinnaker.bumpdeps

import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NonDestructivePropertiesEditorTest {

    private fun subject(): NonDestructivePropertiesEditor =
        Files.createTempFile("gradle", ".properties").let {
            it.toFile().deleteOnExit()
            NonDestructivePropertiesEditor(it)
        }

    @Test
    fun testParsesLines() {
        val subject = subject()

        val lines = listOf(
            "#comment",
            "foo=bar"
        )

        val result = subject.updateProperty(lines, "foo", "bar")
        assertResult(result, lines, expectedMatched = true, expectedUpdated = false)
    }

    @Test
    fun testIgnoresWhitespace() {
        val subject = subject()

        val lines = listOf(
            "  foo  =  bar  "
        )

        val result = subject.updateProperty(lines, "foo", "bar")
        assertResult(result, lines, expectedMatched = true, expectedUpdated = false)
    }

    @Test
    fun testIgnoresCommentedOutValue() {
        val subject = subject()

        val lines = listOf(
            "#foo=bar"
        )

        val result = subject.updateProperty(lines, "foo", "bar")
        assertResult(result, lines, expectedMatched = false, expectedUpdated = false)
    }

    @Test
    fun testUpdatesValue() {
        val subject = subject()

        val lines = listOf(
            "foo=baz"
        )
        val result = subject.updateProperty(lines, "foo", "bar")

        val expected = listOf(
            "foo=bar"
        )
        assertResult(result, expected, expectedMatched = true, expectedUpdated = true)
    }

    @Test
    fun testUpdatedValueTrimsWhitespace() {
        val subject = subject()

        val lines = listOf(
            "   foo  =    baz   "
        )
        val result = subject.updateProperty(lines, "foo", "bar")

        val expected = listOf(
            "foo=bar"
        )
        assertResult(result, expected, expectedMatched = true, expectedUpdated = true)
    }

    @Test
    fun testLastValueForPropertyInFileDeterminesUpdated() {
        val subject = subject()

        val lines = listOf(
            "   foo  =    bar   ",
            "foo=baz"
        )
        val result = subject.updateProperty(lines, "foo", "bar")

        val expected = listOf(
            "   foo  =    bar   ",
            "foo=bar"
        )
        assertResult(result, expected, expectedMatched = true, expectedUpdated = true)
    }

    @Test
    fun testIfNotUpdatedIfLastValueAlreadyMatchedDespiteMutatingIntermediateLines() {
        val subject = subject()

        val lines = listOf(
            "   foo  =    bar   ",
            "foo=baz"
        )
        val result = subject.updateProperty(lines, "foo", "baz")

        val expected = listOf(
            "foo=baz",
            "foo=baz"
        )
        assertResult(result, expected, expectedMatched = true, expectedUpdated = false)
    }

    @Test
    fun testReadsFromPropertiesFile() {
        val subject = subject()
        val file = subject.propertiesFile

        val props = Properties()

        props.setProperty("foo", "bar")
        FileOutputStream(file.toFile()).use {
            props.store(it, "the comments")
        }

        val expected = Files.readAllLines(file, subject.propsCharset)
        val result = subject.updateProperty("foo", "bar")

        assertResult(result, expected, expectedMatched = true, expectedUpdated = false)
    }

    @Test
    fun testWritesToPropertiesFile() {
        val subject = subject()

        val file = subject.propertiesFile

        val props = Properties()

        props.setProperty("foo", "baz")
        FileOutputStream(file.toFile()).use {
            props.store(it, "the comments")
        }

        // this sucks as a test but work around the indeterminate date header
        val expected = Files.readAllLines(file, subject.propsCharset)

        val result = subject.updateProperty("foo", "bar")
        expected[2] = "foo=bar"
        assertResult(result, expected, expectedMatched = true, expectedUpdated = true)

        subject.saveResult(result.lines)

        FileInputStream(file.toFile()).use {
            props.load(it)
        }

        assertEquals("bar", props.getProperty("foo"))
    }

    private fun assertResult(result: Result, expectedLines: List<String>, expectedMatched: Boolean, expectedUpdated: Boolean) {
        assertEquals(expectedMatched, result.matched, "whether the key was matched in the properties file")
        assertEquals(expectedUpdated, result.updated, "whether the effective value in the properties file was updated")
        assertEquals(expectedLines.size, result.lines.size, "expected number of lines")
        for ((index, line) in expectedLines.withIndex()) {
            assertEquals(line, result.lines[index], "expected line ${index + 1} to match")
        }
    }
}
