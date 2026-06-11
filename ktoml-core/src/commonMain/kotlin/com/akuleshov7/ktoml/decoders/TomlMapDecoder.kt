package com.akuleshov7.ktoml.decoders

import com.akuleshov7.ktoml.Toml.Default.serializersModule
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.exceptions.IllegalTypeException
import com.akuleshov7.ktoml.exceptions.InternalDecodingException
import com.akuleshov7.ktoml.tree.nodes.*
import com.akuleshov7.ktoml.tree.nodes.TomlKeyValue
import com.akuleshov7.ktoml.tree.nodes.pairs.keys.TomlKey
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule

/**
 * A single flattened map entry: its full dotted-path key and the leaf AST node it decodes from.
 */
private typealias FlattenedMapEntry = Pair<String, TomlNode>

/**
 * Sometimes, when you do not know the names of the TOML keys and cannot create a proper class with field names for parsing,
 * it can be useful to read and parse TOML tables to a map. This is exactly what this TomlMapDecoder is used for.
 *
 * PLEASE NOTE, THAT IT IS EXTREMELY UNSAFE TO USE THIS DECODER, AS IT WILL DECODE EVERYTHING, WITHOUT ANY TYPE-CHECKING
 *
 * @param rootNode toml table that we are trying to decode
 * @param fullTableKey fullTableKey for the current table
 * @param decodingElementIndex for iterating over the TOML table we are currently reading
 * @param kotlinxIndex for iteration inside the kotlinX loop: [decodeElementIndex -> decodeSerializableElement]
 * @param config TomlInput config
 * @property serializersModule
 */
@ExperimentalSerializationApi
public class TomlMapDecoder private constructor(
    private val rootNode: TomlNode,
    private val fullTableKey: TomlKey,
    private val config: TomlInputConfig,
    private var decodingElementIndex: Int = 0,
    private var kotlinxIndex: Int = 0,
    override val serializersModule: SerializersModule,
) : TomlAbstractDecoder() {
    /**
     * Flattened `dotted-path -> leaf node` entries, lazily built the first time we discover that the
     * map's *value* type is a class/object (see [resolveFlattening]). When non-null, decoding iterates
     * over these entries instead of [rootNode]'s direct children, so an arbitrarily nested set of
     * tables is decoded into a flat `Map<String, T>` keyed by the full dotted path (issue #39).
     */
    private var flattenedEntries: List<FlattenedMapEntry>? = null
    private var isFlatteningResolved: Boolean = false

    public constructor(
        rootNode: TomlTable,
        config: TomlInputConfig,
        decodingElementIndex: Int = 0,
        kotlinxIndex: Int = 0,
        serializersModule: SerializersModule,
    ) : this(
        rootNode = rootNode,
        fullTableKey = rootNode.fullTableKey,
        config = config,
        decodingElementIndex = decodingElementIndex,
        kotlinxIndex = kotlinxIndex,
        serializersModule = serializersModule,
    )

    public constructor(
        rootNode: TomlFile,
        config: TomlInputConfig,
        decodingElementIndex: Int = 0,
        kotlinxIndex: Int = 0,
        serializersModule: SerializersModule,
    ) : this(
        rootNode = rootNode,
        fullTableKey = TomlKey(listOf("")),
        config = config,
        decodingElementIndex = decodingElementIndex,
        kotlinxIndex = kotlinxIndex,
        serializersModule = serializersModule,
    )

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        resolveFlattening(descriptor)

        flattenedEntries?.let { entries ->
            decodingElementIndex = kotlinxIndex / 2
            return if (decodingElementIndex == entries.size) {
                CompositeDecoder.DECODE_DONE
            } else {
                kotlinxIndex++
            }
        }

        // stubs are internal technical nodes that are not needed in this scenario
        if (rootNode.children[decodingElementIndex] is TomlStubEmptyNode) {
            skipStubs()
        } else {
            // we will iterate in the following way:
            // for [map]
            // a = 1
            // b = 2
            // kotlinxIndex will be (0, 1), (2 ,3)
            // and decodingElementIndex will be 0, 1 (as there are only two elements in the table: 'a' and 'b')
            decodingElementIndex = kotlinxIndex / 2
        }

        if (decodingElementIndex == rootNode.children.size) {
            return CompositeDecoder.DECODE_DONE
        }

        return kotlinxIndex++
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        flattenedEntries?.let { entries ->
            val (key, node) = entries[decodingElementIndex]
            @Suppress("UNCHECKED_CAST")
            return if (index % 2 == 0) {
                key as T
            } else {
                decodeFlattenedValue(node, deserializer)
            }
        }

        // stubs are internal technical nodes that are not needed in this scenario
        skipStubs()
        return when (val processedNode = rootNode.children[decodingElementIndex]) {
            is TomlKeyValueArray -> if (index % 2 == 0) {
                processedNode.key.toString() as T
            } else {
                decodeTomlKeyValueArray(processedNode, deserializer)
            }
            is TomlKeyValue -> if (index % 2 == 0) {
                processedNode.key.toString() as T
            } else {
                decodeTomlKeyValue(processedNode, deserializer)
            }
            is TomlTable -> if (index % 2 == 0) {
                processedNode.name as T
            } else {
                decodeTomlTable(processedNode, deserializer)
            }
            else -> throw InternalDecodingException("Trying to decode ${processedNode.prettyStr()} with TomlMapDecoder, " +
                    "but faced an unknown type of Node")
        }
    }

    override fun decodeKeyValue(): TomlKeyValue {
        throw IllegalTypeException("""
            You are trying to decode a nested Table $fullTableKey with a <Map> type to some primitive type.
            For example: 
            [a]
              [a.b]
                  a = 2
                  
            should be decoded to Map<String, Map<String, Long>>, but not to Map<String, Long>
            """, rootNode.lineNo)
    }

    /**
     * TomlStubs are internal technical nodes that should be skipped during the decoding process.
     * And so we need to skip them with our iteration indices:
     * decodingElementIndex had step equals to 1
     * kotlinxIndex has step equals to 2 (because in kotlinx.serialization Maps have x2 index: one for key and one for value)
     */
    private fun skipStubs() {
        if (rootNode.children[decodingElementIndex] is TomlStubEmptyNode) {
            ++decodingElementIndex
            kotlinxIndex += 2
        }
    }

    private fun <T> decodeTomlKeyValue(
        processedNode: TomlNode,
        deserializer: DeserializationStrategy<T>
    ): T = if (deserializer.descriptor.kind == StructureKind.MAP) {
        decodeSerializableValue(deserializer)
    } else {
        // To have a type check, delegate decoding to TomlMainDecoder
        var rootNode = TomlFile()
        rootNode.appendChild(processedNode)
        TomlMainDecoder.decode(deserializer, rootNode, config)
    }

    private fun <T> decodeTomlKeyValueArray(
        processedNode: TomlKeyValueArray,
        deserializer: DeserializationStrategy<T>
    ): T = TomlArrayDecoder.decode(deserializer, processedNode, config)

    private fun <T> decodeTomlTable(
        processedNode: TomlTable,
        deserializer: DeserializationStrategy<T>
    ): T = if (deserializer.descriptor.kind == StructureKind.CLASS) {
        var rootNode = TomlFile()
        rootNode.children.addAll(processedNode.children)
        TomlMainDecoder.decode(deserializer, rootNode, config)
    } else {
        TomlMapDecoder(
            processedNode,
            config,
            serializersModule = serializersModule,
        ).decodeSerializableValue(deserializer)
    }

    /**
     * Decides, once per decoder, whether nested tables should be flattened into dotted-path keys.
     * We only flatten when the map's *value* type is a class/object: that is the case that currently
     * throws (a path-prefix table has no scalar fields to fill the value class), so enabling it is
     * additive. Map/primitive/collection values keep their existing one-level decoding semantics.
     */
    private fun resolveFlattening(descriptor: SerialDescriptor) {
        if (isFlatteningResolved) {
            return
        }
        isFlatteningResolved = true

        if (descriptor.kind != StructureKind.MAP) {
            return
        }
        val valueKind = descriptor.getElementDescriptor(1).kind
        if (valueKind == StructureKind.CLASS || valueKind == StructureKind.OBJECT) {
            flattenedEntries = mutableListOf<FlattenedMapEntry>()
                .also { collectFlattenedEntries(rootNode.children, prefix = "", out = it) }
        }
    }

    /**
     * Walks the table tree collecting leaf entries keyed by their full dotted path (relative to this
     * map's root). A table that holds at least one key/value pair is itself a value and is decoded
     * whole (its own sub-tables, if any, are left to it); a table that holds only sub-tables is a pure
     * path prefix that contributes no entry of its own and is recursed into.
     */
    private fun collectFlattenedEntries(
        children: List<TomlNode>,
        prefix: String,
        out: MutableList<FlattenedMapEntry>
    ) {
        children.forEach { child ->
            when (child) {
                is TomlStubEmptyNode -> Unit
                is TomlTable -> {
                    val key = if (prefix.isEmpty()) child.name else "$prefix.${child.name}"
                    val isPathPrefix = child.children.none { it !is TomlTable && it !is TomlStubEmptyNode }
                    if (isPathPrefix) {
                        collectFlattenedEntries(child.children, key, out)
                    } else {
                        out += key to child
                    }
                }
                is TomlKeyValue -> {
                    val key = if (prefix.isEmpty()) child.key.toString() else "$prefix.${child.key}"
                    out += key to child
                }
                else -> Unit
            }
        }
    }

    private fun <T> decodeFlattenedValue(
        node: TomlNode,
        deserializer: DeserializationStrategy<T>
    ): T = when (node) {
        is TomlKeyValueArray -> decodeTomlKeyValueArray(node, deserializer)
        is TomlKeyValue -> decodeTomlKeyValue(node, deserializer)
        is TomlTable -> decodeTomlTable(node, deserializer)
        else -> throw InternalDecodingException("Trying to decode ${node.prettyStr()} with TomlMapDecoder, " +
                "but faced an unknown type of Node")
    }

    public companion object {
        /**
         * @param deserializer - deserializer provided by Kotlin compiler
         * @param rootNode - root node for decoding (created after parsing)
         * @param config - decoding configuration for parsing and serialization
         * @return decoded (deserialized) object of type T
         */
        public fun <T> decode(
            deserializer: DeserializationStrategy<T>,
            rootNode: TomlFile,
            config: TomlInputConfig = TomlInputConfig()
        ): T {
            val decoder = TomlMapDecoder(
                rootNode,
                config,
                serializersModule = serializersModule,
            )
            return decoder.decodeSerializableValue(deserializer)
        }
    }
}
