package io.spinnaker.bumpdeps

import java.io.FileWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

class NonDestructivePropertiesEditor(internal val propertiesFile: Path) {
    internal val propsCharset: Charset = Charset.forName("8859_1")

    fun updateProperty(key: String, value: String): Result =
        updateProperty(Files.readAllLines(propertiesFile, propsCharset), key, value)

    fun updateProperty(lines: List<String>, key: String, value: String): Result {
        val trimmedKey = key.trim()
        val regex = Pattern.compile("^\\s*${Pattern.quote(trimmedKey)}\\s*=(.*)$")

        val modified = AtomicBoolean(false)
        val exists = AtomicBoolean(false)
        return lines.map processLine@{ line ->
            val matcher = regex.matcher(line)
            if (matcher.matches()) {
                exists.set(true)
                val current = matcher.group(1)?.trim()
                modified.set(current != value)
                if (modified.get()) {
                    return@processLine "$key=$value"
                }
            }
            line
        }.let {
            Result(it, exists.get(), modified.get())
        }
    }

    fun saveResult(lines: List<String>) {
        FileWriter(propertiesFile.toFile(), Charset.forName("8859_1")).use { fw ->
            lines.forEach { line ->
                fw.write(line)
                fw.write("\n")
            }
        }
    }
}

data class Result(val lines: List<String>, val matched: Boolean, val updated: Boolean)
