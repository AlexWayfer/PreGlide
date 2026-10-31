package name.alexwayfer.preglide.sync

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OtherDevicesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun theFreeEditionMergesNoOtherDevicesEvenWhenTheirFilesAreThere() {
        val folder = File(temporary.root, "personal")
        File(folder, "devices/phone").mkdirs()
        File(folder, "devices/phone/ru.tsv").writeText("w\tпривет\t2\t9\t0\n")
        File(folder, "devices/phone/device.tsv").writeText("Pixel\n")

        assertEquals(0, otherDevices { folder }("ru").size)
        assertEquals(emptyMap<String, String>(), otherDeviceNames(folder))
    }
}
