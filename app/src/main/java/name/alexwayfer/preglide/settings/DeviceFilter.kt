package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.engine.suggest.Learned
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.allOn

/** Whose learned words the page shows, kept across a restart by its [key]. */
sealed interface DeviceFilter {
    val key: String

    /** The words of every device, their uses added up. */
    data object All : DeviceFilter {
        override val key = "*"
    }

    data object ThisDevice : DeviceFilter {
        override val key = ""
    }

    /** Another device, by its [id], under the [name] it gave itself, or numbered by [number] among those without. */
    data class Other(val id: String, val name: String?, val number: Int?) : DeviceFilter {
        override val key get() = id
    }
}

/**
 * The filters of the words of the other devices [ids], which [names] names: every device, this one, then the named
 * ones by name and those without a name numbered, when there is more than one; none without other devices.
 */
fun deviceFilters(ids: Set<String>, names: Map<String, String>): List<DeviceFilter> {
    if (ids.isEmpty()) return emptyList()
    val (named, unnamed) = ids.partition { it in names }
    val byName = named.sortedBy { names.getValue(it).lowercase() }.map { DeviceFilter.Other(it, names[it], null) }
    val numbered = unnamed.sorted().mapIndexed { index, id ->
        DeviceFilter.Other(id, null, (index + 1).takeIf { unnamed.size > 1 })
    }
    return listOf(DeviceFilter.All, DeviceFilter.ThisDevice) + byName + numbered
}

/** The filter of [key] among these, or [DeviceFilter.All] once it is gone, as a device that left the account. */
fun List<DeviceFilter>.byKey(key: String): DeviceFilter = firstOrNull { it.key == key } ?: DeviceFilter.All

/** The words [filter] shows of these, in no order. */
fun PersonalWords.shownBy(filter: DeviceFilter): List<Learned> = when (filter) {
    DeviceFilter.All -> all()
    DeviceFilter.ThisDevice -> allOn(null)
    is DeviceFilter.Other -> allOn(filter.id)
}
