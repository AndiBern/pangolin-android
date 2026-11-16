package net.pangolin.Pangolin.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigManager private constructor(context: Context) {
    private val tag = "ConfigManager"
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<Config> = _config.asStateFlow()
    
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "overrideDns", "tunnelDns", "primaryDNSServer", "secondaryDNSServer", "logCollectionEnabled", "mtu", "persistentVpnNotification", "exitNodeTakesPrecedence", ExcludedApplications.PREF_KEY,
            ExcludedApplications.ALLOWED_PREF_KEY, ExcludedApplications.MODE_PREF_KEY -> {
                Log.d(tag, "Preference changed: $key, reloading config")
                _config.value = loadConfig()
            }
        }
    }
    
    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadConfig(): Config {
        return try {
            Config(
                // Must match android:defaultValue in root_preferences.xml (and olm's default):
                // the Settings screen shows aliases as enabled until the user changes it.
                dnsOverrideEnabled = prefs.getBoolean("overrideDns", true),
                dnsTunnelEnabled = prefs.getBoolean("tunnelDns", false),
                primaryDNSServer = prefs.getString("primaryDNSServer", null),
                secondaryDNSServer = prefs.getString("secondaryDNSServer", null),
                logCollectionEnabled = prefs.getBoolean("logCollectionEnabled", false),
                mtu = prefs.getString("mtu", null)?.toIntOrNull(),
                persistentVpnNotification = prefs.getBoolean("persistentVpnNotification", false),
                exitNodeTakesPrecedence = prefs.getBoolean("exitNodeTakesPrecedence", false),
                // getStringSet returns SharedPreferences' internal cached instance; copy it so Config never shares (or mutates) it
                excludedApplications = prefs.getStringSet(ExcludedApplications.PREF_KEY, null)?.toSet() ?: emptySet(),
                vpnAppMode = prefs.getString(ExcludedApplications.MODE_PREF_KEY, null) ?: ExcludedApplications.MODE_EXCLUDE,
                allowedApplications = prefs.getStringSet(ExcludedApplications.ALLOWED_PREF_KEY, null)?.toSet() ?: emptySet()
            )
        } catch (e: Exception) {
            Log.e(tag, "Error loading config: ${e.message}", e)
            Config()
        }
    }

    fun save(config: Config): Boolean {
        return try {
            prefs.edit().apply {
                putBoolean("overrideDns", config.dnsOverrideEnabled ?: true)
                putBoolean("tunnelDns", config.dnsTunnelEnabled ?: false)
                putString("primaryDNSServer", config.primaryDNSServer)
                putString("secondaryDNSServer", config.secondaryDNSServer)
                putBoolean("logCollectionEnabled", config.logCollectionEnabled ?: false)
                putBoolean("persistentVpnNotification", config.persistentVpnNotification)
                putBoolean("exitNodeTakesPrecedence", config.exitNodeTakesPrecedence ?: false)
                putStringSet(ExcludedApplications.PREF_KEY, config.excludedApplications)
                putString(ExcludedApplications.MODE_PREF_KEY, config.vpnAppMode)
                putStringSet(ExcludedApplications.ALLOWED_PREF_KEY, config.allowedApplications)
                if (config.mtu != null) putString("mtu", config.mtu.toString()) else remove("mtu")
                apply()
            }
            _config.value = config
            true
        } catch (e: Exception) {
            Log.e(tag, "Error saving config: ${e.message}", e)
            false
        }
    }

    /**
     * Re-reads the configuration from storage and publishes it. Callers that act on the
     * configuration (starting the tunnel, showing log status) use this instead of the
     * cached value so they never act on a stale copy.
     */
    fun reload(): Config {
        val config = loadConfig()
        _config.value = config
        return config
    }

    fun updateConfig(block: (Config) -> Config) {
        val newConfig = block(_config.value)
        save(newConfig)
    }
    
    fun cleanup() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    companion object {
        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }

}
