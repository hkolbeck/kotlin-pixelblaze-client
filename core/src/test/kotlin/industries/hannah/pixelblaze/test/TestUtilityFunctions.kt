package industries.hannah.pixelblaze.test

import industries.hannah.pixelblaze.Pixelblaze
import industries.hannah.pixelblaze.WebsocketPixelblaze
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class TestUtilityFunctions {

    @ParameterizedTest(name = "{0} => {1}")
    @CsvSource(
        "sliderVarName, Var Name",
        "varName, 'Var Name'",
        "VarName, 'Var Name'",
        "varNameLOL, 'Var Name L O L'", //TODO: Handle this better
        "'', ''"
    )
    fun testHumanizeVarName(raw: String, expected: String) {
        assertEquals(expected, Pixelblaze.humanizeVarName(raw))
    }

    /**
     * Neither of these should throw for lack of a required field
     */
    @Test
    fun testDefaultBuilder() {
        Pixelblaze.default().close()
        WebsocketPixelblaze.defaultBuilder().build().close()
    }
}