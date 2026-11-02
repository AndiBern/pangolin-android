package net.pangolin.Pangolin.util

/** Decides which apps bypass the VPN interface, and how the user edits that list. */
internal object ExcludedApplications {
    /** SharedPreferences key of the in-app exclusion list (a string set of package names). */
    const val PREF_KEY = "excludedApplications"

    /** Android 17, which added Settings.ACTION_VPN_APP_EXCLUSION_SETTINGS. */
    const val SYSTEM_SETTINGS_MIN_SDK = 37

    /**
     * Packages to pass to VpnService.Builder.addDisallowedApplication(). Our own package is never
     * excluded, so the app's API traffic keeps following the tunnel like it always has.
     */
    fun forConfig(config: Config, ownPackage: String): List<String> =
        config.excludedApplications.filter { it.isNotBlank() && it != ownPackage }.sorted()

    enum class Editor {
        /** Android's own exclusion screen; changes apply to a running VPN immediately. */
        SYSTEM_SETTINGS,
        /** Pangolin's app picker; applied via addDisallowedApplication() on the next connect. */
        IN_APP
    }

    /**
     * The system screen is preferred where it exists. It's optional for device makers, so
     * Android 17+ devices that don't ship it fall back to the in-app picker.
     */
    fun editorFor(sdkInt: Int, systemSettingsResolvable: Boolean): Editor =
        if (sdkInt >= SYSTEM_SETTINGS_MIN_SDK && systemSettingsResolvable) Editor.SYSTEM_SETTINGS
        else Editor.IN_APP

    /**
     * The in-app list is applied on every connect regardless of the editor. When the system
     * screen is the main editor, the in-app picker stays available as a second entry: Android's
     * screen doesn't list system packages (e.g. Android Auto), and a list picked before an
     * upgrade to Android 17 must stay reviewable instead of silently excluding apps.
     */
    fun showOtherAppsEntry(editor: Editor): Boolean = editor == Editor.SYSTEM_SETTINGS

    /** An installed app the user can pick; icons are kept by the UI. */
    data class InstalledApp(val packageName: String, val label: String)

    /**
     * Excluded apps first so the current choice is visible at a glance, then alphabetical.
     * Excluded packages that aren't installed (or visible) any more are kept as rows labelled
     * by package name, so they can still be removed.
     */
    fun displayOrder(installed: List<InstalledApp>, excluded: Set<String>): List<InstalledApp> {
        val known = installed.mapTo(HashSet()) { it.packageName }
        val missing = excluded.filter { it !in known }.map { InstalledApp(it, it) }
        return (installed + missing).sortedWith(
            compareBy<InstalledApp> { it.packageName !in excluded }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy { it.packageName }
        )
    }

    /** Case-insensitive match on label or package name; order is preserved. */
    fun filter(apps: List<InstalledApp>, query: String): List<InstalledApp> {
        val q = query.trim()
        if (q.isEmpty()) return apps
        return apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
}
