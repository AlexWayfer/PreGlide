package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.engine.suggest.Learned
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.learn
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceFilterTest {
    @Test
    fun withoutOtherDevicesThereIsNothingToFilter() {
        assertEquals(emptyList<DeviceFilter>(), deviceFilters(emptySet(), emptyMap()))
    }

    @Test
    fun allAndThisDeviceComeFirstThenTheNamedByNameThenTheUnnamedNumbered() {
        val filters = deviceFilters(setOf("b", "x2", "a", "x1"), mapOf("a" to "ноутбук", "b" to "Компьютер"))

        assertEquals(
            listOf(
                DeviceFilter.All,
                DeviceFilter.ThisDevice,
                DeviceFilter.Other("b", "Компьютер", number = null),
                DeviceFilter.Other("a", "ноутбук", number = null),
                DeviceFilter.Other("x1", name = null, number = 1),
                DeviceFilter.Other("x2", name = null, number = 2),
            ),
            filters,
        )
    }

    @Test
    fun aLoneUnnamedDeviceGoesWithoutANumber() {
        assertEquals(
            DeviceFilter.Other("pc", name = null, number = null),
            deviceFilters(setOf("pc"), emptyMap()).last(),
        )
    }

    @Test
    fun aFilterIsFoundByItsKeyAndOneGoneFallsBackToAll() {
        val filters = deviceFilters(setOf("pc"), mapOf("pc" to "ПК"))

        assertEquals(DeviceFilter.ThisDevice, filters.byKey(DeviceFilter.ThisDevice.key))
        assertEquals(DeviceFilter.Other("pc", "ПК", number = null), filters.byKey("pc"))
        assertEquals(DeviceFilter.All, filters.byKey("tablet"))
    }

    @Test
    fun eachFilterShowsTheWordsOfItsDevices() {
        val words = PersonalWords().apply { learn("привет", previous = null, time = 5) }
        words.mergeOthers(mapOf("pc" to PersonalWords.read(sequenceOf("w\tпривет\t2\t9\t0", "w\tкотлин\t1\t7\t0"))))

        assertEquals(setOf(Learned("привет", 3, 9), Learned("котлин", 1, 7)), words.shownBy(DeviceFilter.All).toSet())
        assertEquals(listOf(Learned("привет", 1, 5)), words.shownBy(DeviceFilter.ThisDevice))
        assertEquals(
            setOf(Learned("привет", 2, 9), Learned("котлин", 1, 7)),
            words.shownBy(DeviceFilter.Other("pc", "ПК", number = null)).toSet(),
        )
    }
}
