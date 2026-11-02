package net.pangolin.Pangolin.util

import net.pangolin.Pangolin.util.ExcludedApplications.Editor
import net.pangolin.Pangolin.util.ExcludedApplications.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedApplicationsTest {
    private val own = "net.pangolin.Pangolin"

    @Test
    fun nothingIsExcludedByDefault() {
        assertTrue(ExcludedApplications.forConfig(Config(), own).isEmpty())
    }

    @Test
    fun selectedAppsAreExcludedInStableOrder() {
        val config = Config(excludedApplications = setOf("com.b", "com.a", "com.google.android.projection.gearhead"))
        assertEquals(
            listOf("com.a", "com.b", "com.google.android.projection.gearhead"),
            ExcludedApplications.forConfig(config, own)
        )
    }

    @Test
    fun ownPackageAndBlankEntriesAreNeverExcluded() {
        val config = Config(excludedApplications = setOf(own, "", " ", "com.a"))
        assertEquals(listOf("com.a"), ExcludedApplications.forConfig(config, own))
    }

    @Test
    fun systemScreenIsUsedOnAndroid17WhenItResolves() {
        assertEquals(Editor.SYSTEM_SETTINGS, ExcludedApplications.editorFor(37, systemSettingsResolvable = true))
        assertEquals(Editor.SYSTEM_SETTINGS, ExcludedApplications.editorFor(38, systemSettingsResolvable = true))
    }

    @Test
    fun inAppPickerIsUsedBeforeAndroid17OrWhenTheScreenIsMissing() {
        assertEquals(Editor.IN_APP, ExcludedApplications.editorFor(36, systemSettingsResolvable = true))
        assertEquals(Editor.IN_APP, ExcludedApplications.editorFor(24, systemSettingsResolvable = false))
        assertEquals(Editor.IN_APP, ExcludedApplications.editorFor(37, systemSettingsResolvable = false))
    }

    @Test
    fun otherAppsEntryIsAlwaysOfferedNextToTheSystemScreen() {
        // Android's screen doesn't list system packages such as Android Auto
        assertTrue(ExcludedApplications.showOtherAppsEntry(Editor.SYSTEM_SETTINGS))
        // Before Android 17 the main entry already is the in-app picker
        assertFalse(ExcludedApplications.showOtherAppsEntry(Editor.IN_APP))
    }

    @Test
    fun displayOrderPutsExcludedFirstThenSortsByLabel() {
        val apps = listOf(
            InstalledApp("com.z", "zebra"),
            InstalledApp("com.b", "Banana"),
            InstalledApp("com.a", "apple"),
            InstalledApp("com.y", "Yak"),
        )
        assertEquals(
            listOf("com.y", "com.z", "com.a", "com.b"),
            ExcludedApplications.displayOrder(apps, setOf("com.z", "com.y")).map { it.packageName }
        )
    }

    @Test
    fun displayOrderKeepsExcludedAppsThatAreNoLongerInstalled() {
        val result = ExcludedApplications.displayOrder(listOf(InstalledApp("com.a", "Apple")), setOf("com.gone"))
        assertEquals(listOf(InstalledApp("com.gone", "com.gone"), InstalledApp("com.a", "Apple")), result)
    }

    @Test
    fun filterMatchesLabelOrPackageIgnoringCase() {
        val apps = listOf(
            InstalledApp("com.google.android.projection.gearhead", "Android Auto"),
            InstalledApp("com.spotify.music", "Spotify"),
        )
        assertEquals(listOf("Android Auto"), ExcludedApplications.filter(apps, " auto ").map { it.label })
        assertEquals(listOf("Spotify"), ExcludedApplications.filter(apps, "SPOTIFY.mus").map { it.label })
        assertEquals(apps, ExcludedApplications.filter(apps, ""))
        assertTrue(ExcludedApplications.filter(apps, "nothing").isEmpty())
    }
}
