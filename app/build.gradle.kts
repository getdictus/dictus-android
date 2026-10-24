import com.android.build.api.artifact.SingleArtifact
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * The git revision the binary was built from, resolved at configuration time.
 *
 * WHY inject it rather than read git at runtime: the APK has no repository to ask, and a
 * field report has to state which commit produced it. Every log export then carries the
 * revision with no manual discipline, which is what makes "are you on the right build?"
 * answerable from the file alone (#112).
 *
 * Returns "unknown" outside a working tree (a source archive, a CI checkout without git).
 * A build with no revision information has to keep saying so rather than guess.
 */
fun gitOutput(vararg command: String): String = try {
    val output = ByteArrayOutputStream()
    val result = providers.exec {
        commandLine(*command)
        isIgnoreExitValue = true
    }
    result.result.get().exitValue.let { if (it != 0) "unknown" else result.standardOutput.asText.get().trim() }
        .ifEmpty { "unknown" }
} catch (_: Exception) {
    "unknown"
}

/**
 * The version of a build that release.yml did not make, derived from the git tags by
 * `.github/scripts/derive-android-version.sh --dev`: `1.2.0-17-g44a3cd6` and a versionCode
 * between the last release and the next one. The script holds the formula once, for
 * release and dev builds alike, so the two cannot drift (#165).
 *
 * WHY a ValueSource: Gradle runs it only when a task needs the version, and never when
 * release.yml provides VERSION_NAME / VERSION_CODE.
 *
 * Never fails the build: without bash, git or any release tag (a shallow CI checkout), it
 * logs a warning and reports 0.0.0-unknown or 0.0.0-dev+g<sha> with versionCode 1.
 */
abstract class DevVersionSource : ValueSource<Map<String, String>, DevVersionSource.Params> {
    interface Params : ValueSourceParameters {
        val script: RegularFileProperty
        val repoDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): Map<String, String> {
        val logger = Logging.getLogger(DevVersionSource::class.java)
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val values = try {
            execOperations.exec {
                commandLine("bash", parameters.script.get().asFile.absolutePath, "--dev")
                workingDir = parameters.repoDir.get().asFile
                standardOutput = output
                errorOutput = errors
            }
            output.toString().lines().mapNotNull { line ->
                line.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
        } catch (e: Exception) {
            logger.warn("warning: could not derive the version from git (${e.message})")
            emptyMap()
        }
        errors.toString().trim().takeIf { it.isNotEmpty() }?.let { logger.warn(it) }
        val name = values["version_name"] ?: "0.0.0-unknown"
        val code = values["version_code"]?.toIntOrNull() ?: 1
        logger.lifecycle("Dictus version from git: $name ($code)")
        return mapOf("version_name" to name, "version_code" to code.toString())
    }
}

val devVersion = providers.of(DevVersionSource::class) {
    parameters {
        script.set(rootProject.layout.projectDirectory.file(".github/scripts/derive-android-version.sh"))
        repoDir.set(rootProject.layout.projectDirectory)
    }
}

// release.yml's VERSION_NAME / VERSION_CODE, derived from the pushed tag, win over git.
val appVersionName: Provider<String> = providers.environmentVariable("VERSION_NAME")
    .orElse(devVersion.map { it.getValue("version_name") })
val appVersionCode: Provider<Int> = providers.environmentVariable("VERSION_CODE")
    .map { it.toInt() }
    .orElse(devVersion.map { it.getValue("version_code").toInt() })

/**
 * Fails the build when a 64-bit native library in an APK or AAB has an ELF LOAD segment
 * aligned below 16 KB, or is stored compressed in an APK (#218). An AAB always holds its
 * libraries compressed: bundletool decides how the APKs it builds from it store them.
 *
 * WHY: Google Play requires 16 KB page support for apps targeting API 35+, and a 4 KB
 * aligned library fails to load (UnsatisfiedLinkError) on an Android 15+ device that uses
 * 16 KB pages. A new native dependency or an NDK change could silently bring 4 KB back;
 * hooked into assemble and bundle, CI's assembleDebug catches it.
 *
 * 32-bit ABIs are listed in the report but not enforced: the requirement covers 64-bit only.
 */
abstract class CheckNativePageAlignment : DefaultTask() {
    /** APK output directories or AAB files. */
    @get:InputFiles
    abstract val archives: ConfigurableFileCollection

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val lines = mutableListOf<String>()
        val failures = mutableListOf<String>()
        archives.asFileTree.filter { it.extension == "apk" || it.extension == "aab" }.sortedBy { it.name }.forEach { archive ->
            ZipFile(archive).use { zip ->
                zip.entries().asSequence()
                    .mapNotNull { entry -> LIB_PATH.find(entry.name)?.let { entry to it.groupValues[1] } }
                    .forEach { (entry, abi) ->
                        val align = minLoadAlignment(zip.getInputStream(entry).readBytes())
                        val stored = entry.method == ZipEntry.STORED
                        val line = "${archive.name}!${entry.name} align=0x${align.toString(16)} " +
                            (if (stored) "stored" else "compressed")
                        lines += line
                        val mustBeStored = archive.extension == "apk"
                        if (abi in ABIS_64 && (align < PAGE_16K || (mustBeStored && !stored))) failures += line
                    }
            }
        }
        report.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
        if (failures.isNotEmpty()) {
            throw GradleException(
                "64-bit native libraries must be stored uncompressed with 16 KB LOAD alignment " +
                    "(see #218):\n" + failures.joinToString("\n"),
            )
        }
    }

    /** Smallest p_align of the PT_LOAD segments of a little-endian ELF32 or ELF64 file. */
    private fun minLoadAlignment(elf: ByteArray): Long {
        val buffer = ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.getInt(0) == 0x464C457F) { "not an ELF file" }
        val is64 = elf[4].toInt() == 2
        val phoff = if (is64) buffer.getLong(0x20).toInt() else buffer.getInt(0x1C)
        val phentsize = buffer.getShort(if (is64) 0x36 else 0x2A).toInt()
        val phnum = buffer.getShort(if (is64) 0x38 else 0x2C).toInt()
        return (0 until phnum).map { phoff + it * phentsize }
            .filter { buffer.getInt(it) == PT_LOAD }
            .minOf { if (is64) buffer.getLong(it + 0x30) else buffer.getInt(it + 0x1C).toLong() }
    }

    private companion object {
        val LIB_PATH = Regex("""(?:^|/)lib/([^/]+)/[^/]+\.so$""")
        val ABIS_64 = setOf("arm64-v8a", "x86_64")
        const val PAGE_16K = 16384L
        const val PT_LOAD = 1
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.licensee)
}

android {
    namespace = "dev.pivisolutions.dictus"
    compileSdk = 35

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            val keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            val keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            val storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "dev.pivisolutions.dictus"
        minSdk = 29
        targetSdk = 35
        // versionCode and versionName come from git tags, see appVersionName above (#165).
        // Injected into BuildConfig so every log export names the exact commit (#112).
        buildConfigField("String", "GIT_SHA", "\"${gitOutput("git", "rev-parse", "--short=12", "HEAD")}\"")
        buildConfigField("String", "GIT_BRANCH", "\"${gitOutput("git", "rev-parse", "--abbrev-ref", "HEAD")}\"")
        buildConfigField(
            "boolean",
            "GIT_DIRTY",
            (gitOutput("git", "status", "--porcelain").let { it != "unknown" && it.isNotEmpty() }).toString(),
        )
    }

    buildTypes {
        // Parakeet 0.6B v3 RAM gate: 6 GB, like iOS (#148, measured on a 6 GB Pixel 4).
        // Release always keeps 6; a debug build can move it to test another tier:
        //   ./gradlew assembleDebug -Pdictus.parakeetMinRamGb=8
        // Expected: testDebugUnitTest run with the property set to anything but 6 fails
        // ModelCatalogTest, DeviceRamTest and ModelsViewModelTest, which assert the 6 GB
        // gate. Run unit tests without it.
        debug {
            val parakeetMinRamGb = providers.gradleProperty("dictus.parakeetMinRamGb").orNull?.let { raw ->
                val gb = raw.toIntOrNull()
                require(gb != null && gb in 1..16) {
                    "dictus.parakeetMinRamGb must be an integer from 1 to 16 (GB), got '$raw'"
                }
                gb
            } ?: 6
            buildConfigField("int", "PARAKEET_MIN_RAM_GB", parakeetMinRamGb.toString())
        }
        release {
            buildConfigField("int", "PARAKEET_MIN_RAM_GB", "6")
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        jniLibs {
            pickFirsts += setOf("**/libc++_shared.so")
            // Keep native libraries uncompressed and 16 KB zip-aligned in the APK, loaded in
            // place rather than extracted (the default for minSdk 23+, pinned for #218).
            useLegacyPackaging = false
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("CC-BY-4.0")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
}

// Copy licensee artifacts.json into APK assets so LicencesScreen can read it at runtime.
// The licensee plugin generates the file in build/reports/ but does not bundle it as an asset.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.versionName.set(appVersionName)
            output.versionCode.set(appVersionCode)
        }

        val variantName = variant.name
        val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }
        val outputDir = layout.buildDirectory.dir("generated/licensee_assets/$variantName")

        val copyTask = tasks.register("copyLicenseeAssets$capitalizedVariant") {
            dependsOn("licenseeAndroid$capitalizedVariant")
            inputs.file(layout.buildDirectory.file("reports/licensee/android$capitalizedVariant/artifacts.json"))
            outputs.dir(outputDir)
            doLast {
                val src = layout.buildDirectory.file("reports/licensee/android$capitalizedVariant/artifacts.json").get().asFile
                val dest = outputDir.get().asFile.resolve("app/cash/licensee/artifacts.json")
                dest.parentFile.mkdirs()
                src.copyTo(dest, overwrite = true)
            }
        }

        variant.sources.assets?.addStaticSourceDirectory(outputDir.get().asFile.absolutePath)

        // 16 KB page-size guard on the packaged APK and AAB (#218), see CheckNativePageAlignment.
        val apkAlignment = tasks.register<CheckNativePageAlignment>("check${capitalizedVariant}ApkNativeAlignment") {
            archives.from(variant.artifacts.get(SingleArtifact.APK))
            report.set(layout.buildDirectory.file("reports/native-alignment/${variantName}-apk.txt"))
        }
        val bundleAlignment = tasks.register<CheckNativePageAlignment>("check${capitalizedVariant}BundleNativeAlignment") {
            archives.from(variant.artifacts.get(SingleArtifact.BUNDLE))
            report.set(layout.buildDirectory.file("reports/native-alignment/${variantName}-bundle.txt"))
        }
        tasks.matching { it.name == "assemble$capitalizedVariant" }.configureEach { dependsOn(apkAlignment) }
        tasks.matching { it.name == "bundle$capitalizedVariant" }.configureEach { dependsOn(bundleAlignment) }

        tasks.matching { it.name == "merge${capitalizedVariant}Assets" }.configureEach {
            dependsOn(copyTask)
        }

        // Every lint task that scans generated assets must wait for the copied report.
        tasks.matching {
            (it.name.startsWith("lint") || it.name.contains("Lint")) &&
                it.name.contains(capitalizedVariant)
        }.configureEach {
            dependsOn(copyTask)
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ime"))
    implementation(project(":whisper"))
    implementation(project(":asr"))
    implementation(libs.okhttp)
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.compose.material.icons.extended)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.timber)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.work.runtime)
    ksp(libs.room.compiler)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.room.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
