package app.tenet.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup plus a walk through the main tabs (Heute, Sport with its three
 * disciplines, Journal, Ernährung, Optionen) with some scrolling: these
 * paths end up in the Baseline Profile. The test installs a fresh app, so
 * it first goes through the welcome screen as a guest.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "app.tenet.android", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        finishWelcome()
        // Intro animation, then the "Heute" page.
        device.wait(Until.hasObject(By.desc("Heute")), 5_000)
        device.waitForIdle()
        listOf("Journal", "Tagebuch", "Träume", "Ernährung", "Rezepte", "Optionen", "Sport", "Calisthenics", "Laufen", "Heute").forEach { tab ->
            val found = tap(tab)
            android.util.Log.i("BaselineProfileGen", "$tab found=$found in ${device.currentPackageName}")
            // Plain swipes up only: the lists recompose while scrolling (UI objects
            // would go stale), and a swipe down at the top is pull-to-refresh,
            // which opens Health Connect on the running page.
            val x = device.displayWidth / 2
            val h = device.displayHeight
            repeat(2) {
                device.swipe(x, h * 7 / 10, x, h * 3 / 10, 12)
                device.waitForIdle()
            }
            // The tab bar hides while scrolling down: a short pull back shows it
            // again (too short for pull-to-refresh).
            device.swipe(x, h * 45 / 100, x, h * 50 / 100, 10)
            device.waitForIdle()
            // Something opened outside the app: come back.
            if (device.currentPackageName != packageName) {
                device.pressBack()
                device.waitForIdle()
            }
        }
    }

    /** Welcome screen of a fresh install: guest, keep the defaults, permissions later. */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.finishWelcome() {
        device.wait(Until.hasObject(By.text("Als Gast fortfahren")), 3_000) || return
        tap("Als Gast fortfahren")
        repeat(8) {
            if (device.hasObject(By.desc("Heute"))) return
            if (!tap("Weiter") && !tap("Später")) return
        }
    }

    /**
     * Clicks the element with this content description or text; Compose may
     * replace the node between finding and clicking, so retry a few times.
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.tap(name: String): Boolean {
        repeat(3) {
            val ok = runCatching {
                val node = device.findObject(By.desc(name)) ?: device.findObject(By.text(name)) ?: return false
                node.click()
            }.isSuccess
            device.waitForIdle()
            if (ok) return true
        }
        return false
    }
}
