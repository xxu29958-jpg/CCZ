package com.ccz.core.rng

/**
 * Deterministic splitmix64 PRNG. The state is a single Long and must be saved
 * with battle state for replay and save/load determinism.
 */
class Rng private constructor(initial: Long) {
    var state: Long = initial
        private set

    private fun next(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be > 0" }
        val bits = next() ushr 1
        return (bits % bound.toLong()).toInt()
    }

    fun nextPercent(): Int = nextInt(100)

    fun snapshot(): Long = state

    companion object {
        fun seed(seed: Long): Rng = Rng(seed)
        fun restore(state: Long): Rng = Rng(state)
    }
}

