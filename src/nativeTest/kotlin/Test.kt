import com.github.ryderhuggins.i32ToByteArray
import kotlin.test.Test

class CalculatorTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testSum() {
        println("result: ${i32ToByteArray(123).toHexString()}")
    }



}