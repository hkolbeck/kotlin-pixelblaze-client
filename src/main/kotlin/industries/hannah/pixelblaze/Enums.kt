package industries.hannah.pixelblaze

enum class ConnectionEvent {
    Connected,
    ScheduledMessageEnqueueFailure,
    WatchThreadException,
    WatcherFailed,
    ClientDisconnect,
}

enum class BinaryTypeFlag(val typeVal: Byte) {
    PutSource(1),
    PutByteCode(3),
    PreviewImage(4),
    PreviewFrame(5),
    GetSource(6),
    GetProgramList(7),
    PutPixelMap(8),
    ExpanderChannels(9);

    companion object {
        fun fromByte(byte: Byte): BinaryTypeFlag? {
            return when (byte.toInt()) {
                1 -> PutSource //Unsupported
                3 -> PutByteCode //Unsupported
                4 -> PreviewImage
                5 -> PreviewFrame
                6 -> GetSource //Unsupported
                7 -> GetProgramList //Unsupported
                8 -> PutPixelMap //Unsupported
                9 -> ExpanderChannels
                else -> null
            }
        }
    }
}

enum class FramePosition(val typeVal: Byte) {
    First(1),
    Middle(2),
    Last(4),
    Only(5);

    companion object {
        fun fromByte(byte: Byte): FramePosition? {
            return when (byte.toInt()) {
                1 -> First
                2 -> Middle
                4 -> Last
                5 -> Only
                else -> null
            }
        }
    }
}


enum class LedType(val typeVal: Int) {
    None(0),
    APA102_SK9822_DOTSTAR(1),
    WS2812_SK6812_NEOPIXEL(2),
    WS2801(3),
    OutputExpander(5);

    companion object {
        fun fromInt(int: Int): LedType? {
            return when (int) {
                0 -> None
                1 -> APA102_SK9822_DOTSTAR
                2 -> WS2812_SK6812_NEOPIXEL
                3 -> WS2801
                5 -> OutputExpander
                else -> null
            }
        }
    }
}

enum class ChannelType(val typeVal: Int) {
    Unknown(0),
    WS2812(1),
    APA102Data(3),
    APA102Clock(4);

    companion object {
        fun fromInt(int: Int): ChannelType? {
            return when (int) {
                0 -> Unknown
                1 -> WS2812
                3 -> APA102Data
                4 -> APA102Clock
                else -> null
            }
        }
    }
}

enum class RenderType(val typeVal: Int) {
    Invalid(0),
    _1D(1),
    _2D(2),
    _3D(3);

    companion object {
        fun fromInt(int: Int): RenderType? {
            return when (int) {
                0 -> Invalid
                1 -> _1D
                2 -> _2D
                3 -> _3D
                else -> null
            }
        }
    }
}

enum class InputSource(val typeVal: Int) {
    Remote(0),
    Local(1);

    companion object {
        fun fromInt(int: Int): InputSource? {
            return when (int) {
                0 -> Remote
                1 -> Local
                else -> null
            }
        }
    }
}


enum class SequencerMode(val typeVal: Int) {
    Off(0),
    ShuffleAll(1),
    Playlist(2);

    companion object {
        fun fromInt(int: Int): SequencerMode? {
            return when (int) {
                0 -> Off
                1 -> ShuffleAll
                2 -> Playlist
                else -> null
            }
        }
    }
}


enum class ColorOrder(val str: String) {
    BGR("BGR"),
    BRG("BRG"),
    GBR("GBR"),
    RBG("RBG"),
    GRB("GRB"),
    RGB("RGB"),
    WGRB("WGRB"),
    WRGB("WRGB"),
    GRBW("GRBW"),
    RGBW("RGBW");

    companion object {
        fun fromString(str: String): ColorOrder? {
            return when (str) {
                "BGR" -> BGR
                "BRG" -> BRG
                "GBR" -> GBR
                "RBG" -> RBG
                "GRB" -> GRB
                "RGB" -> RGB
                "WGRB" -> WGRB
                "WRGB" -> WRGB
                "GRBW" -> GRBW
                "RGBW" -> RGBW
                else -> null
            }
        }

        fun fromInt(int: Int): ColorOrder? {
            return when (int) {
                6 -> BGR
                9 -> BRG
                18 -> GBR
                24 -> RBG
                33 -> GRB
                36 -> RGB
                54 -> WGRB
                57 -> WRGB
                225 -> GRBW
                228 -> RGBW
                else -> null
            }
        }
    }
}
