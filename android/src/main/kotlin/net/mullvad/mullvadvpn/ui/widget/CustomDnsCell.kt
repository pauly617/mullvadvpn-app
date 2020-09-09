package net.mullvad.mullvadvpn.ui.widget

import android.content.Context
import android.text.method.DigitsKeyListener
import android.util.AttributeSet
import android.widget.TextView
import java.net.InetAddress
import org.apache.commons.validator.routines.InetAddressValidator

class CustomDnsCell : InputCell {
    companion object {
        private val STARTING_SLASH = Regex("^/")
    }

    var value: InetAddress?
        get() {
            val address = text

            if (isValid(address)) {
                return InetAddress.getByName(address)
            } else {
                return null
            }
        }
        set(value) {
            text = (value?.toString() ?: "").replace(STARTING_SLASH, "")
        }

    var onSubmitDnsServer: ((InetAddress?) -> Unit)? = null

    constructor(context: Context) : super(context, TextView(context)) {}

    constructor(context: Context, attributes: AttributeSet) :
        super(context, attributes, TextView(context)) {}

    constructor(context: Context, attributes: AttributeSet, defaultStyleAttribute: Int) :
        super(context, attributes, defaultStyleAttribute, TextView(context)) {}

    constructor(
        context: Context,
        attributes: AttributeSet,
        defaultStyleAttribute: Int,
        defaultStyleResource: Int
    ) : super(
        context,
        attributes,
        defaultStyleAttribute,
        defaultStyleResource,
        TextView(context)
    ) {}

    init {
        input.apply {
            @Suppress("DEPRECATION")
            keyListener = object : DigitsKeyListener() {
                override fun getAcceptedChars() = "01234567890abcdefABCDEF.:".toCharArray()
            }
        }

        isValidInput = { input -> isValid(input) }

        onSubmitText = { input ->
            if (isValid(input)) {
                onSubmitDnsServer?.invoke(InetAddress.getByName(input))
            } else {
                onSubmitDnsServer?.invoke(null)
            }
        }
    }

    private fun isValid(input: String) = InetAddressValidator.getInstance().isValid(input)
}
