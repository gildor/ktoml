package com.akuleshov7.ktoml.file

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.okio.encodeToBufferedSink

import okio.use

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Writes to a file in the TOML format.
 * @property serializersModule
 */
@Deprecated(
    "Open a sink with your chosen filesystem and use the ktoml-okio or " +
            "ktoml-kotlinx-io extension APIs. This class remains available for compatibility."
)
@Suppress("SINGLE_CONSTRUCTOR_SHOULD_BE_PRIMARY")
public open class TomlFileWriter : Toml {
    public constructor(
        inputConfig: TomlInputConfig = TomlInputConfig(),
        outputConfig: TomlOutputConfig = TomlOutputConfig(),
        serializersModule: SerializersModule = EmptySerializersModule(),
    ) : super(
        inputConfig,
        outputConfig,
        serializersModule
    )

    public fun <T> encodeToFile(
        serializer: SerializationStrategy<T>,
        value: T,
        tomlFilePath: String
    ) {
        openFileForWrite(tomlFilePath).use {
            encodeToBufferedSink(serializer, value, it)
        }
    }
}
