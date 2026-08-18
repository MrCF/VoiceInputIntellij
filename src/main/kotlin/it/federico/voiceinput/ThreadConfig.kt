package it.federico.voiceinput

object ThreadConfig {

    val available: Int
        get() =
            Runtime.getRuntime()
                .availableProcessors()
                .coerceAtLeast(1)

    val recommended: Int
        get() =
            (available / 2)
                .coerceAtLeast(2)
                .coerceAtMost(32)
                .coerceAtMost(available)

    val minimum: Int
        get() = 1

    val maximum: Int
        get() = available
}
