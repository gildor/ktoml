package com.akuleshov7.ktoml.decoders

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlDecoder
import com.akuleshov7.ktoml.TomlElement
import com.akuleshov7.ktoml.TomlElementKind
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.annotations.ExperimentalKtomlApi
import com.akuleshov7.ktoml.decodeFromTomlElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalKtomlApi::class)
class TomlElementTest {
    @Serializable
    private data class Known(val host: String)

    private data class RawSection(
        val keys: Set<String>,
        val values: Map<String, String>,
        val format: Toml,
    )

    private object RawSectionSerializer : KSerializer<RawSection> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RawSection")

        override fun deserialize(decoder: Decoder): RawSection {
            val input = decoder as? TomlDecoder
                ?: throw SerializationException("RawSection can only be decoded from TOML")
            val element = input.decodeTomlElement()
            return RawSection(
                element.keys(),
                input.toml.decodeFromTomlElement(element),
                input.toml,
            )
        }

        override fun serialize(encoder: Encoder, value: RawSection): Nothing =
            error("Decode-only test serializer")
    }

    @Serializable
    private data class Document(
        val known: Known,
        @Serializable(with = RawSectionSerializer::class)
        val unknown: RawSection,
    )

    @Test
    fun exposesCurrentNestedTableAndActiveFormat() {
        val format = Toml(inputConfig = TomlInputConfig(ignoreUnknownNames = true))
        val decoded = format.decodeFromString(
            Document.serializer(),
            """
                [known]
                host = "localhost"

                [unknown]
                first = "one"
                second = "two"
            """.trimIndent(),
        )

        assertEquals(Known("localhost"), decoded.known)
        assertEquals(setOf("first", "second"), decoded.unknown.keys)
        assertEquals(mapOf("first" to "one", "second" to "two"), decoded.unknown.values)
        assertSame(format, decoded.unknown.format)
    }

    private data class TableNames(val names: Set<String>)

    private object TableNamesSerializer : KSerializer<TableNames> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TableNames")

        override fun deserialize(decoder: Decoder): TableNames = TableNames(
            (decoder as TomlDecoder).decodeTomlElement().tables().keys
        )

        override fun serialize(encoder: Encoder, value: TableNames): Nothing =
            error("Decode-only test serializer")
    }

    @Test
    fun exposesDocumentTablesToTopLevelSerializer() {
        val result = Toml.decodeFromString(
            TableNamesSerializer,
            """
                [server]
                host = "localhost"

                [cache]
                url = "redis://localhost"
            """.trimIndent(),
        )

        assertEquals(setOf("server", "cache"), result.names)
    }

    private data class RawString(val value: String)

    private object RawStringSerializer : KSerializer<RawString> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RawString", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): RawString {
            val input = decoder as TomlDecoder
            return RawString(input.toml.decodeFromTomlElement(input.decodeTomlElement()))
        }

        override fun serialize(encoder: Encoder, value: RawString): Nothing =
            error("Decode-only test serializer")
    }

    @Serializable
    private data class StringList(
        @Serializable(with = RawStringListSerializer::class)
        val values: List<RawString>,
    )

    private object RawStringListSerializer : KSerializer<List<RawString>> by
        kotlinx.serialization.builtins.ListSerializer(RawStringSerializer)

    @Test
    fun exposesCurrentArrayElement() {
        val result = Toml.decodeFromString(StringList.serializer(), "values = [\"one\", \"two\"]")

        assertEquals(listOf(RawString("one"), RawString("two")), result.values)
    }

    @Test
    fun parsesOnceAndDecodesTablesOnDemand() {
        val document: TomlElement = Toml.parseToTomlElement(
            """
                title = "example"
                [server]
                host = "localhost"
            """.trimIndent()
        )

        assertEquals(setOf("title", "server"), document.keys())
        assertEquals(Known("localhost"), Toml.decodeFromTomlElement(document.tables().getValue("server")))
    }

    @Test
    fun decodesArrayOfTablesElement() {
        val document = Toml.parseToTomlElement(
            """
                [[server]]
                host = "first"

                [[server]]
                host = "second"
            """.trimIndent()
        )
        val servers = document.tables().getValue("server")

        assertEquals(TomlElementKind.ARRAY, servers.kind)
        assertEquals(
            listOf(Known("first"), Known("second")),
            Toml.decodeFromTomlElement<List<Known>>(servers),
        )
    }
}
