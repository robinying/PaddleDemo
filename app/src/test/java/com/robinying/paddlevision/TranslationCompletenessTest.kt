package com.robinying.paddlevision

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the translation gap the 2026-09-10 audit found: new UI copy landed only in the default
 * `values/` directory, so `lintDebug` failed with 44 MissingTranslation errors and every foreign
 * locale mixed languages.
 *
 * Lint covers `<string>` entries but not `<string-array>` items or plural quantities, so this
 * test compares the full resource name set of every locale against the default one, requires the
 * mandatory `other` plural quantity, and checks that translations keep the same positional format
 * arguments. Run it whenever a string resource is added.
 */
class TranslationCompletenessTest {
    private val resourceDirectory = findResourceDirectory()
    private val defaultLocaleDirectory = resourceDirectory.resolve("values")

    @Test
    fun everyLocaleDeclaresTheSameResourceNamesAsTheDefaultLocale() {
        val expected = resourceNames(defaultLocaleDirectory)
        assertTrue("Expected the default locale to declare string resources", expected.strings.isNotEmpty())

        localeDirectories().forEach { (locale, directory) ->
            val actual = resourceNames(directory)
            assertEquals("$locale is missing string resources", expected.strings, actual.strings)
            assertEquals("$locale is missing string arrays", expected.arrays, actual.arrays)
            assertEquals("$locale is missing plurals", expected.plurals, actual.plurals)
        }
    }

    @Test
    fun everyLocaleHasAtLeastOneTranslatedResource() {
        assertTrue("Expected to find translation directories", localeDirectories().isNotEmpty())

        localeDirectories().forEach { (locale, directory) ->
            assertFalse("$locale declares no resources at all", resourceNames(directory).isEmpty())
        }
    }

    @Test
    fun everyPluralDeclaresTheMandatoryOtherQuantity() {
        (listOf("values" to defaultLocaleDirectory) + localeDirectories().toList()).forEach { (locale, directory) ->
            pluralQuantities(directory).forEach { (name, quantities) ->
                assertTrue(
                    "$locale plural '$name' is missing the mandatory 'other' quantity",
                    "other" in quantities,
                )
                assertTrue("$locale plural '$name' declares no quantities", quantities.isNotEmpty())
            }
        }
    }

    @Test
    fun translationsKeepThePositionalFormatArgumentsOfTheDefaultLocale() {
        val expected = stringBodies(defaultLocaleDirectory)

        localeDirectories().forEach { (locale, directory) ->
            val actual = stringBodies(directory)
            expected.forEach { (name, body) ->
                val translated = actual[name] ?: return@forEach
                assertEquals(
                    "$locale changed the positional format arguments of '$name'",
                    formatArguments(body),
                    formatArguments(translated),
                )
            }
        }
    }

    private data class ResourceNames(
        val strings: Set<String>,
        val arrays: Set<String>,
        val plurals: Set<String>,
    ) {
        fun isEmpty(): Boolean = strings.isEmpty() && arrays.isEmpty() && plurals.isEmpty()
    }

    private fun resourceNames(directory: File): ResourceNames = ResourceNames(
        strings = elementNames(directory.resolve(STRINGS_FILE), "string"),
        arrays = elementNames(directory.resolve(ARRAYS_FILE), "string-array"),
        plurals = elementNames(directory.resolve(STRINGS_FILE), "plurals"),
    )

    private fun elementNames(file: File, tagName: String): Set<String> {
        if (!file.isFile) return emptySet()
        val nodes = parse(file).getElementsByTagName(tagName)
        return (0 until nodes.length).mapTo(mutableSetOf()) { index ->
            (nodes.item(index) as Element).getAttribute("name")
        }
    }

    private fun stringBodies(directory: File): Map<String, String> {
        val file = directory.resolve(STRINGS_FILE)
        if (!file.isFile) return emptyMap()
        val nodes = parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private fun pluralQuantities(directory: File): Map<String, Set<String>> {
        val file = directory.resolve(STRINGS_FILE)
        if (!file.isFile) return emptyMap()
        val pluralNodes = parse(file).getElementsByTagName("plurals")
        return (0 until pluralNodes.length).associate { index ->
            val plural = pluralNodes.item(index) as Element
            val items = plural.getElementsByTagName("item")
            plural.getAttribute("name") to (0 until items.length).mapTo(mutableSetOf()) { itemIndex ->
                (items.item(itemIndex) as Element).getAttribute("quantity")
            }
        }
    }

    private fun formatArguments(body: String): Set<String> =
        FORMAT_ARGUMENT.findAll(body).mapTo(mutableSetOf()) { it.value }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance()
        .apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        .newDocumentBuilder()
        .parse(file)

    private fun localeDirectories(): Map<String, File> = resourceDirectory.listFiles()
        .orEmpty()
        .filter { directory ->
            directory.isDirectory &&
                directory.name.startsWith(LOCALE_PREFIX) &&
                (directory.resolve(STRINGS_FILE).isFile || directory.resolve(ARRAYS_FILE).isFile)
        }
        .associateBy(File::getName)

    private fun findResourceDirectory(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull(File::isDirectory)
            ?: error("Could not locate src/main/res from ${File("").absolutePath}")
    }

    private companion object {
        const val STRINGS_FILE = "strings.xml"
        const val ARRAYS_FILE = "arrays.xml"
        const val LOCALE_PREFIX = "values-"
        val FORMAT_ARGUMENT = Regex("""%(\d+)\$[a-zA-Z]""")
    }
}
