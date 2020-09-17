package net.mullvad.mullvadvpn.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.mullvad.mullvadvpn.BuildConfig
import net.mullvad.mullvadvpn.R
import net.mullvad.mullvadvpn.dataproxy.MullvadProblemReport
import net.mullvad.mullvadvpn.service.MullvadVpnService
import net.mullvad.talpid.util.EventNotifier

class MainActivity : FragmentActivity() {
    companion object {
        val KEY_SHOULD_CONNECT = "should_connect"
    }

    val problemReport = MullvadProblemReport()
    val serviceNotifier = EventNotifier<ServiceConnection?>(null)

    private var isUiVisible = false
    private var quitting = false
    private var service: MullvadVpnService.LocalBinder? = null
    private var serviceConnection: ServiceConnection? = null
    private var shouldConnect = false
    private var visibleSecureScreens = HashSet<Fragment>()

    private val serviceConnectionManager = object : android.content.ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            android.util.Log.d("mullvad", "UI successfully connected to the service")
            val localBinder = binder as MullvadVpnService.LocalBinder

            service = localBinder

            localBinder.isUiVisible = isUiVisible

            localBinder.serviceNotifier.subscribe(this@MainActivity) { service ->
                android.util.Log.d("mullvad", "UI connection to the service changed: $service")
                serviceConnection?.onDestroy()

                val newConnection = service?.let { safeService ->
                    ServiceConnection(safeService, this@MainActivity)
                }

                serviceConnection = newConnection
                serviceNotifier.notify(newConnection)

                if (shouldConnect) {
                    tryToConnect()
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            android.util.Log.d("mullvad", "UI lost the connection to the service")
            service?.serviceNotifier?.unsubscribe(this@MainActivity)
            serviceConnection = null
            serviceNotifier.notify(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        quitting = false

        problemReport.logDirectory.complete(filesDir)

        setContentView(R.layout.main)

        if (savedInstanceState == null) {
            addInitialFragment()
        }

        if (intent.getBooleanExtra(KEY_SHOULD_CONNECT, false)) {
            shouldConnect = true
            tryToConnect()
        }
    }

    override fun onStart() {
        android.util.Log.d("mullvad", "Starting main activity")
        super.onStart()

        isUiVisible = true

        if (!quitting) {
            android.util.Log.d("mullvad", "Starting background service")
            val intent = Intent(this, MullvadVpnService::class.java)

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            bindService(intent, serviceConnectionManager, 0)
            service?.isUiVisible = true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        setVpnPermission(resultCode == Activity.RESULT_OK)
    }

    override fun onStop() {
        android.util.Log.d("mullvad", "Stoping main activity")
        isUiVisible = false
        service?.isUiVisible = false
        unbindService(serviceConnectionManager)

        super.onStop()
    }

    override fun onDestroy() {
        serviceNotifier.unsubscribeAll()
        serviceConnection?.onDestroy()

        super.onDestroy()
    }

    fun enterSecureScreen(screen: Fragment) {
        synchronized(this) {
            visibleSecureScreens.add(screen)

            if (!BuildConfig.DEBUG) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    fun leaveSecureScreen(screen: Fragment) {
        synchronized(this) {
            visibleSecureScreens.remove(screen)

            if (!BuildConfig.DEBUG && visibleSecureScreens.isEmpty()) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    fun openSettings() {
        supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.fragment_enter_from_bottom,
                R.anim.do_nothing,
                R.anim.do_nothing,
                R.anim.fragment_exit_to_bottom
            )
            replace(R.id.main_fragment, SettingsFragment())
            addToBackStack(null)
            commit()
        }
    }

    fun returnToLaunchScreen() {
        supportFragmentManager?.apply {
            popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

            beginTransaction().apply {
                replace(R.id.main_fragment, LaunchFragment())
                commit()
            }
        }
    }

    fun requestVpnPermission(intent: Intent) {
        startActivityForResult(intent, 0)
    }

    fun quit() {
        quitting = true
        service?.stop()
        finishAndRemoveTask()
    }

    private fun tryToConnect() {
        serviceConnection?.apply {
            connectionProxy.connect()
            shouldConnect = false
        }
    }

    private fun addInitialFragment() {
        supportFragmentManager?.beginTransaction()?.apply {
            add(R.id.main_fragment, LaunchFragment())
            commit()
        }
    }

    private fun setVpnPermission(allow: Boolean) = GlobalScope.launch(Dispatchers.Default) {
        serviceConnection?.connectionProxy?.vpnPermission?.complete(allow)
    }
}
