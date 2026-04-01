package dev.pivisolutions.dictus.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pivisolutions.dictus.R
import dev.pivisolutions.dictus.core.theme.DictusColors
import dev.pivisolutions.dictus.core.theme.LocalDictusColors

/**
 * Dedicated licences screen matching iOS LicensesView.
 *
 * Displays OSS licenses in two sections:
 * 1. Auto-generated Maven dependencies from cashapp/licensee artifacts.json
 * 2. Manual entries for vendored/non-Maven dependencies (whisper.cpp, sherpa-onnx, Parakeet, Dictus)
 *
 * WHY this approach: cashapp/licensee auto-discovers all transitive Maven dependencies at
 * build time and bundles artifacts.json into Android assets. This ensures the license list
 * never goes stale. Non-Maven deps (NDK source, model weights) are manually listed below.
 */
@Composable
fun LicencesScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.licences_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.licences_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val openLink = { url: String ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // Load auto-generated licenses from licensee plugin (generated at build time)
        val mavenLicenses = remember {
            try {
                val json = context.assets.open("app/cash/licensee/artifacts.json")
                    .bufferedReader()
                    .use { it.readText() }
                parseLicenseeArtifacts(json)
            } catch (e: Exception) {
                emptyList()
            }
        }

        // Render auto-generated Maven dependency entries first
        mavenLicenses.forEach { dep ->
            LicenceBlock(
                name = "${dep.groupId}:${dep.artifactId}",
                author = "v${dep.version}",
                url = dep.scmUrl ?: dep.spdxUrl,
                licenceText = "${dep.spdxName}\n${dep.spdxUrl}",
                onLinkClick = openLink,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section header for vendored / non-Maven dependencies
        if (mavenLicenses.isNotEmpty()) {
            Text(
                text = "Vendored & Non-Maven",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // whisper.cpp — vendored NDK source in third_party/whisper.cpp (MIT)
        LicenceBlock(
            name = "whisper.cpp",
            author = "Georgi Gerganov",
            url = "https://github.com/ggerganov/whisper.cpp",
            licenceText = mitLicence("Copyright (c) 2023-2026 The ggml authors"),
            onLinkClick = openLink,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // sherpa-onnx — Kotlin API vendored as source in asr/ module (Apache 2.0)
        LicenceBlock(
            name = "sherpa-onnx",
            author = "Next-gen Kaldi (k2-fsa)",
            url = "https://github.com/k2-fsa/sherpa-onnx",
            licenceText = "Apache License, Version 2.0\n\nCopyright (c) 2023-2026 k2-fsa\n\nLicensed under the Apache License, Version 2.0. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0",
            onLinkClick = openLink,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // NVIDIA Parakeet models — data assets, not code (CC-BY-4.0)
        LicenceBlock(
            name = "NVIDIA Parakeet",
            author = "NVIDIA Corporation",
            url = "https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3",
            licenceText = "CC-BY-4.0\n\nCopyright (c) 2024-2026 NVIDIA Corporation\n\nParakeet models are licensed under Creative Commons Attribution 4.0 International (CC-BY-4.0).",
            onLinkClick = openLink,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Dictus itself
        LicenceBlock(
            name = "Dictus",
            author = "Get Dictus",
            url = "https://github.com/getdictus/dictus",
            licenceText = mitLicence("Copyright (c) 2026 Get Dictus"),
            onLinkClick = openLink,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Parsed representation of a single entry from cashapp/licensee artifacts.json.
 *
 * artifacts.json structure:
 * [{ "groupId": "...", "artifactId": "...", "version": "...",
 *    "spdxLicenses": [{"identifier": "MIT", "name": "...", "url": "..."}],
 *    "scm": {"url": "https://..."} }]
 */
private data class MavenLicense(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val spdxId: String,
    val spdxName: String,
    val spdxUrl: String,
    val scmUrl: String?,
)

/**
 * Parses the JSON string from cashapp/licensee artifacts.json into a list of MavenLicense entries.
 *
 * Uses org.json.JSONArray from the Android SDK (zero new dependency).
 */
private fun parseLicenseeArtifacts(json: String): List<MavenLicense> {
    val array = org.json.JSONArray(json)
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        val spdx = obj.optJSONArray("spdxLicenses")?.optJSONObject(0)
        val scm = obj.optJSONObject("scm")
        MavenLicense(
            groupId = obj.optString("groupId", ""),
            artifactId = obj.optString("artifactId", ""),
            version = obj.optString("version", ""),
            spdxId = spdx?.optString("identifier", "Unknown") ?: "Unknown",
            spdxName = spdx?.optString("name", "Unknown License") ?: "Unknown License",
            spdxUrl = spdx?.optString("url", "") ?: "",
            scmUrl = scm?.optString("url"),
        )
    }
}

@Composable
private fun LicenceBlock(
    name: String,
    author: String,
    url: String,
    licenceText: String,
    onLinkClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = author,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = url,
            color = DictusColors.Accent,
            fontSize = 14.sp,
            modifier = Modifier.clickable { onLinkClick(url) },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = licenceText,
            color = LocalDictusColors.current.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
        )
    }
}

private fun mitLicence(copyright: String): String = """
MIT License

$copyright

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
""".trimIndent()
