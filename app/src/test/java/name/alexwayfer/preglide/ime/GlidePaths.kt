package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language

/** A glide along the middles of [word]'s keys on the Russian letters. */
internal fun glideThrough(word: String): Glide {
    val places = KeyboardLayout.of(Language.Russian).places
    val points = word.zipWithNext { a, b ->
        val from = places.getValue(a).center
        val to = places.getValue(b).center
        List(4) { KeyPoint(from.x + (to.x - from.x) * it / 4, from.y + (to.y - from.y) * it / 4) }
    }.flatten() + places.getValue(word.last()).center
    return Glide(points)
}
