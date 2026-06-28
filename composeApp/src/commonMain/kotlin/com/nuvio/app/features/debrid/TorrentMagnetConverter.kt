package com.nuvio.app.features.debrid

internal object TorrentMagnetConverter {
    fun convertToMagnet(torrentBytes: ByteArray): String? {
        val parser = BencodeParser(torrentBytes)
        val root = parser.parse() as? BValue.Dict ?: return null
        val info = root.values["info"] as? BValue.Dict ?: return null
        val infoStart = parser.infoStart.takeIf { it >= 0 } ?: return null
        val infoEnd = parser.infoEnd.takeIf { it > infoStart } ?: return null
        val name = info.values["name"]?.asText()?.takeIf { it.isNotBlank() } ?: return null
        val length = info.totalLength()
        val trackers = root.trackers()
        val hash = sha1(torrentBytes.copyOfRange(infoStart, infoEnd)).toBase32()

        return buildString {
            append("magnet:?xt=urn:btih:")
            append(hash)
            append("&dn=")
            append(encodePathSegment(name))
            if (length > 0L) {
                append("&xl=")
                append(length)
            }
            trackers.forEach { tracker ->
                append("&tr=")
                append(encodePathSegment(tracker))
            }
        }
    }
}

private class BencodeParser(
    private val bytes: ByteArray,
) {
    var infoStart: Int = -1
        private set
    var infoEnd: Int = -1
        private set

    fun parse(): BValue? =
        runCatching { parseValue(0).value }.getOrNull()

    private fun parseValue(index: Int): ParseResult {
        if (index !in bytes.indices) error("Unexpected end of bencode")
        return when (bytes[index].toInt().toChar()) {
            'i' -> parseInteger(index)
            'l' -> parseList(index)
            'd' -> parseDictionary(index)
            in '0'..'9' -> parseByteString(index)
            else -> error("Invalid bencode token")
        }
    }

    private fun parseInteger(index: Int): ParseResult {
        var cursor = index + 1
        while (cursor < bytes.size && bytes[cursor].toInt().toChar() != 'e') cursor++
        if (cursor >= bytes.size) error("Unterminated integer")
        val value = bytes.decodeRange(index + 1, cursor).toLong()
        return ParseResult(BValue.Integer(value), cursor + 1)
    }

    private fun parseList(index: Int): ParseResult {
        val values = mutableListOf<BValue>()
        var cursor = index + 1
        while (cursor < bytes.size && bytes[cursor].toInt().toChar() != 'e') {
            val result = parseValue(cursor)
            values += result.value
            cursor = result.nextIndex
        }
        if (cursor >= bytes.size) error("Unterminated list")
        return ParseResult(BValue.ListValue(values), cursor + 1)
    }

    private fun parseDictionary(index: Int): ParseResult {
        val values = linkedMapOf<String, BValue>()
        var cursor = index + 1
        while (cursor < bytes.size && bytes[cursor].toInt().toChar() != 'e') {
            val keyResult = parseByteString(cursor)
            val key = (keyResult.value as BValue.Bytes).text()
            val valueStart = keyResult.nextIndex
            val valueResult = parseValue(valueStart)
            if (key == "info" && infoStart < 0) {
                infoStart = valueStart
                infoEnd = valueResult.nextIndex
            }
            values[key] = valueResult.value
            cursor = valueResult.nextIndex
        }
        if (cursor >= bytes.size) error("Unterminated dictionary")
        return ParseResult(BValue.Dict(values), cursor + 1)
    }

    private fun parseByteString(index: Int): ParseResult {
        var cursor = index
        while (cursor < bytes.size && bytes[cursor].toInt().toChar() != ':') cursor++
        if (cursor >= bytes.size) error("Unterminated byte string length")
        val length = bytes.decodeRange(index, cursor).toInt()
        val start = cursor + 1
        val end = start + length
        if (length < 0 || end > bytes.size) error("Invalid byte string length")
        return ParseResult(BValue.Bytes(bytes.copyOfRange(start, end)), end)
    }

    private fun ByteArray.decodeRange(start: Int, end: Int): String =
        copyOfRange(start, end).decodeToString()
}

private data class ParseResult(
    val value: BValue,
    val nextIndex: Int,
)

private sealed interface BValue {
    data class Integer(val value: Long) : BValue
    data class Bytes(val value: ByteArray) : BValue {
        fun text(): String = value.decodeToString()
    }
    data class ListValue(val values: List<BValue>) : BValue
    data class Dict(val values: Map<String, BValue>) : BValue
}

private fun BValue.asText(): String? =
    (this as? BValue.Bytes)?.text()

private fun BValue.asLong(): Long? =
    (this as? BValue.Integer)?.value

private fun BValue.Dict.totalLength(): Long {
    values["length"]?.asLong()?.let { return it }
    val files = values["files"] as? BValue.ListValue ?: return 0L
    return files.values.sumOf { file ->
        ((file as? BValue.Dict)?.values?.get("length") as? BValue.Integer)?.value ?: 0L
    }
}

private fun BValue.Dict.trackers(): List<String> {
    val trackers = mutableListOf<String>()
    values["announce"]?.asText()?.takeIf { it.isNotBlank() }?.let(trackers::add)
    val announceList = values["announce-list"] as? BValue.ListValue
    announceList?.values.orEmpty().forEach { tier ->
        when (tier) {
            is BValue.ListValue -> tier.values.forEach { tracker ->
                tracker.asText()?.takeIf { it.isNotBlank() }?.let(trackers::add)
            }
            else -> tier.asText()?.takeIf { it.isNotBlank() }?.let(trackers::add)
        }
    }
    return trackers.distinct()
}

private fun ByteArray.toBase32(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    var buffer = 0
    var bitsLeft = 0
    return buildString {
        this@toBase32.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                append(alphabet[(buffer shr (bitsLeft - 5)) and 31])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            append(alphabet[(buffer shl (5 - bitsLeft)) and 31])
        }
    }
}

private fun sha1(input: ByteArray): ByteArray {
    val bitLength = input.size.toLong() * 8L
    val paddedLength = ((input.size + 9 + 63) / 64) * 64
    val message = ByteArray(paddedLength)
    input.copyInto(message)
    message[input.size] = 0x80.toByte()
    for (index in 0 until 8) {
        message[paddedLength - 1 - index] = ((bitLength ushr (index * 8)) and 0xFF).toByte()
    }

    var h0 = 0x67452301
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476
    var h4 = 0xC3D2E1F0.toInt()
    val words = IntArray(80)

    for (chunkStart in message.indices step 64) {
        for (index in 0 until 16) {
            val offset = chunkStart + (index * 4)
            words[index] =
                ((message[offset].toInt() and 0xFF) shl 24) or
                    ((message[offset + 1].toInt() and 0xFF) shl 16) or
                    ((message[offset + 2].toInt() and 0xFF) shl 8) or
                    (message[offset + 3].toInt() and 0xFF)
        }
        for (index in 16 until 80) {
            words[index] = (words[index - 3] xor words[index - 8] xor words[index - 14] xor words[index - 16])
                .rotateLeft(1)
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4

        for (index in 0 until 80) {
            val (f, k) = when (index) {
                in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                else -> (b xor c xor d) to 0xCA62C1D6.toInt()
            }
            val temp = a.rotateLeft(5) + f + e + k + words[index]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = temp
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    return intArrayOf(h0, h1, h2, h3, h4).toByteArrayBigEndian()
}

private fun Int.rotateLeft(bits: Int): Int =
    (this shl bits) or (this ushr (32 - bits))

private fun IntArray.toByteArrayBigEndian(): ByteArray {
    val output = ByteArray(size * 4)
    forEachIndexed { index, value ->
        val offset = index * 4
        output[offset] = (value ushr 24).toByte()
        output[offset + 1] = (value ushr 16).toByte()
        output[offset + 2] = (value ushr 8).toByte()
        output[offset + 3] = value.toByte()
    }
    return output
}
