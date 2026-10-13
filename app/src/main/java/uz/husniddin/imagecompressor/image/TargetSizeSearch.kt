package uz.husniddin.imagecompressor.image

/**
 * Bounded binary search over encoder quality for the highest quality whose *actual*
 * encoded size is ≤ the target. Uses at most ~8 encodes for the 10–100 range.
 */
object TargetSizeSearch {

    class Result(val quality: Int, val bytes: ByteArray, val met: Boolean, val encodeCount: Int)

    fun search(
        targetBytes: Long,
        minQuality: Int,
        maxQuality: Int,
        encode: (quality: Int) -> ByteArray,
    ): Result {
        require(minQuality in 1..maxQuality) { "Invalid quality range" }
        var encodes = 0
        fun run(q: Int): ByteArray = encode(q).also { encodes++ }

        val top = run(maxQuality)
        if (top.size <= targetBytes) return Result(maxQuality, top, met = true, encodeCount = encodes)

        var lo = minQuality
        var hi = maxQuality - 1
        var bestQuality = -1
        var bestBytes: ByteArray? = null
        var smallestBytes: ByteArray = top
        var smallestQuality = maxQuality
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val bytes = run(mid)
            if (bytes.size <= targetBytes) {
                bestQuality = mid
                bestBytes = bytes
                lo = mid + 1
            } else {
                if (bytes.size < smallestBytes.size) {
                    smallestBytes = bytes
                    smallestQuality = mid
                }
                hi = mid - 1
            }
        }
        if (bestBytes != null) return Result(bestQuality, bestBytes, met = true, encodeCount = encodes)
        // Not reachable by quality alone: return the smallest output we produced (normally minQuality).
        if (smallestQuality != minQuality) {
            val min = run(minQuality)
            if (min.size < smallestBytes.size) {
                smallestBytes = min
                smallestQuality = minQuality
            }
        }
        return Result(smallestQuality, smallestBytes, met = false, encodeCount = encodes)
    }
}
