package name.alexwayfer.preglide.ime

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Closer than this to its target, and slower than [SETTLED_SPEED], a value stops on the target. */
private const val SETTLED_DISTANCE = 0.0005f
private const val SETTLED_SPEED = 0.01f

/** Where a springing value is and how fast it moves, in its units per second. */
internal data class SpringState(val value: Float, val velocity: Float = 0f)

/**
 * This state [seconds] later on a critically damped spring of [stiffness] that pulls it to [target]: the fastest
 * motion that never overshoots. The formula is exact for any frame length, so a long frame cannot make the value
 * shoot past or shake, and a target that moves between frames is simply followed from where the value is, at its
 * speed. Once next to the target and nearly still, the value stops on it, so a settled motion stops changing.
 */
internal fun SpringState.toward(target: Float, seconds: Float, stiffness: Float): SpringState {
    val omega = sqrt(stiffness)
    val offset = value - target
    val decay = exp(-omega * seconds)
    val slope = velocity + omega * offset
    val next = SpringState(
        value = target + (offset + slope * seconds) * decay,
        velocity = (velocity - omega * slope * seconds) * decay,
    )
    val settled = abs(next.value - target) < SETTLED_DISTANCE && abs(next.velocity) < SETTLED_SPEED
    return if (settled) SpringState(target) else next
}
