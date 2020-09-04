package net.mullvad.mullvadvpn.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.RelativeLayout
import kotlin.properties.Delegates.observable
import net.mullvad.mullvadvpn.R
import net.mullvad.mullvadvpn.ui.LoginState
import net.mullvad.mullvadvpn.ui.widget.AccountLoginBorder.BorderState

class AccountLogin : RelativeLayout {
    private val container =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).let { service ->
            val inflater = service as LayoutInflater

            inflater.inflate(R.layout.account_login, this)
        }

    private val border: AccountLoginBorder = container.findViewById(R.id.border)
    private val accountHistoryList: ListView = container.findViewById(R.id.history)
    private val input: AccountInput = container.findViewById(R.id.input)

    private val dividerHeight = resources.getDimensionPixelSize(R.dimen.account_history_divider)
    private val historyEntryHeight =
        resources.getDimensionPixelSize(R.dimen.account_history_entry_height)

    private val historyAnimation = ValueAnimator.ofInt(0, 0).apply {
        addUpdateListener { animation ->
            val layoutParams = container.layoutParams

            layoutParams.height = animation.animatedValue as Int

            container.layoutParams = layoutParams
        }

        duration = 350
    }

    private val expandedHeight: Int
        get() = collapsedHeight + historyHeight

    private var historyHeight by observable(0) { _, _, _ ->
        historyAnimation.setIntValues(collapsedHeight, expandedHeight)
    }

    private var collapsedHeight by observable(0) { _, _, newCollapsedHeight ->
        historyAnimation.setIntValues(newCollapsedHeight, expandedHeight)
    }

    private var inputHasFocus by observable(false) { _, _, hasFocus ->
        updateBorder()

        if (hasFocus) {
            shouldShowAccountHistory = true
        }
    }

    private var shouldShowAccountHistory by observable(false) { _, isShown, show ->
        if (isShown != show) {
            if (show) {
                historyAnimation.start()
            } else {
                historyAnimation.reverse()
            }
        }
    }

    var accountHistory by observable<ArrayList<String>?>(null) { _, _, history ->
        val entryCount = history?.size ?: 0

        historyHeight = entryCount * (historyEntryHeight + dividerHeight)
        updateAccountHistory()
    }

    var state: LoginState by observable(LoginState.Initial) { _, _, newState ->
        input.loginState = newState

        updateBorder()

        when (newState) {
            LoginState.Initial -> {}
            LoginState.InProgress -> loggingInState()
            LoginState.Success -> successState()
            LoginState.Failure -> {}
        }
    }

    var onLogin: ((String) -> Unit)?
        get() = input.onLogin
        set(value) { input.onLogin = value }

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attributes: AttributeSet) : super(context, attributes) {}

    constructor(context: Context, attributes: AttributeSet, defaultStyleAttribute: Int) :
        super(context, attributes, defaultStyleAttribute) {}

    constructor(
        context: Context,
        attributes: AttributeSet,
        defaultStyleAttribute: Int,
        defaultStyleResource: Int
    ) : super(context, attributes, defaultStyleAttribute, defaultStyleResource) {
    }

    init {
        border.elevation = elevation + 0.1f

        input.apply {
            onFocusChanged.subscribe(this) { hasFocus ->
                inputHasFocus = hasFocus
            }

            onTextChanged.subscribe(this) { _ ->
                if (state == LoginState.Failure) {
                    state = LoginState.Initial
                }
            }

            addOnLayoutChangeListener(
                OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                    collapsedHeight = bottom - top
                }
            )
        }
    }

    fun onDestroy() {
        input.onFocusChanged.unsubscribe(this)
        input.onTextChanged.unsubscribe(this)
    }

    private fun loggingInState() {
        accountHistoryList.visibility = View.INVISIBLE
    }

    private fun successState() {
        visibility = View.INVISIBLE
    }

    private fun updateAccountHistory() {
        accountHistory?.let { history ->
            accountHistoryList.apply {
                setAdapter(
                    ArrayAdapter(
                        context,
                        R.layout.account_history_entry,
                        R.id.account_history_entry_text_view,
                        history
                    )
                )

                setOnItemClickListener { _, _, idx, _ ->
                    input.loginWith(history[idx])
                }
            }
        }
    }

    private fun updateBorder() {
        if (state == LoginState.Failure) {
            border.borderState = BorderState.ERROR
        } else if (inputHasFocus) {
            border.borderState = BorderState.FOCUSED
        } else {
            border.borderState = BorderState.UNFOCUSED
        }
    }
}
