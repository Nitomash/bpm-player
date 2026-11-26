package android.net

class Uri private constructor(private val value: String) {

    companion object {
        @JvmStatic
        fun parse(value: String): Uri = Uri(value)
    }

    override fun toString(): String = value
}
