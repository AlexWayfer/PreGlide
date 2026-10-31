package name.alexwayfer.preglide.sync

import name.alexwayfer.preglide.engine.suggest.PersonalWords
import java.io.File

/** The free edition does not sync, so it merges in no other device, whatever files are left of premium. */
val otherDevices: (folderOf: () -> File) -> (String) -> Map<String, PersonalWords> = { { emptyMap() } }

/** No other device, so no names of them either. */
val otherDeviceNames: (folder: File) -> Map<String, String> = { emptyMap() }
