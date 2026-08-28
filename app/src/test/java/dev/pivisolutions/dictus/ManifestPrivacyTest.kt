package dev.pivisolutions.dictus

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
class ManifestPrivacyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `application data is excluded from Android backup`() {
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun `merged manifest references both privacy rule resources`() {
        val properties = Properties().apply {
            ManifestPrivacyTest::class.java.getResourceAsStream(
                "/com/android/tools/test_config.properties",
            )!!.use(::load)
        }
        val configuredPath = properties.getProperty("android_merged_manifest")
        val manifestFile = sequenceOf(File(configuredPath), File("app", configuredPath))
            .first(File::isFile)
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifestFile)
        val application = document.getElementsByTagName("application").item(0)
        val androidNamespace = "http://schemas.android.com/apk/res/android"

        assertEquals("false", application.attributes.getNamedItemNS(androidNamespace, "allowBackup").nodeValue)
        assertEquals(
            "@xml/backup_rules",
            application.attributes.getNamedItemNS(androidNamespace, "fullBackupContent").nodeValue,
        )
        assertEquals(
            "@xml/data_extraction_rules",
            application.attributes.getNamedItemNS(androidNamespace, "dataExtractionRules").nodeValue,
        )
    }

    @Test
    fun `legacy backup rules exclude every app data domain`() {
        assertEquals(
            allAppDataDomains,
            excludedDomains(R.xml.backup_rules).getValue("full-backup-content"),
        )
    }

    @Test
    fun `Android 12 rules exclude cloud and device transfer domains`() {
        val exclusions = excludedDomains(R.xml.data_extraction_rules)

        assertEquals(allAppDataDomains, exclusions.getValue("cloud-backup"))
        assertEquals(allAppDataDomains, exclusions.getValue("device-transfer"))
    }

    private fun excludedDomains(resourceId: Int): Map<String, Set<String>> {
        val parser = context.resources.getXml(resourceId)
        val exclusions = mutableMapOf<String, MutableSet<String>>()
        var section: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "full-backup-content", "cloud-backup", "device-transfer" -> section = parser.name
                    "exclude" -> {
                        val domain = parser.getAttributeValue(null, "domain")
                        val path = parser.getAttributeValue(null, "path")
                        if (section != null && path == ".") {
                            exclusions.getOrPut(section) { mutableSetOf() }.add(domain)
                        }
                    }
                }
            } else if (
                parser.eventType == XmlPullParser.END_TAG &&
                parser.name == section &&
                section != "full-backup-content"
            ) {
                section = null
            }
            parser.next()
        }
        parser.close()
        return exclusions
    }

    private companion object {
        val allAppDataDomains = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
    }
}
