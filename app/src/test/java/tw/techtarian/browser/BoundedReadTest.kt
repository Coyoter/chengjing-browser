package tw.techtarian.browser
import org.junit.Test
import org.junit.Assert.*
class BoundedReadTest {
    @Test fun exactLength(){assertArrayEquals(byteArrayOf(1,2,3),byteArrayOf(1,2,3).inputStream().readBounded(3))}
    @Test fun truncatesWithoutOverreading(){val stream=byteArrayOf(1,2,3,4).inputStream();assertArrayEquals(byteArrayOf(1,2),stream.readBounded(2));assertEquals(3,stream.read())}
    @Test fun shorterStream(){assertArrayEquals(byteArrayOf(7),byteArrayOf(7).inputStream().readBounded(50))}
}
