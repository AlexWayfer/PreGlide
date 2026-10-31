package name.alexwayfer.preglide.update

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import name.alexwayfer.preglide.BuildConfig
import name.alexwayfer.preglide.PreGlideApplication
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.settings.PageHeader
import name.alexwayfer.preglide.settings.PageEntry
import name.alexwayfer.preglide.settings.SettingsPage
import name.alexwayfer.preglide.settings.scrollingPage

@Composable
internal fun UpdatesPage(onBack: () -> Unit, onAccess: () -> Unit) {
    val app = LocalContext.current.applicationContext as PreGlideApplication
    val state by app.updates.state.collectAsStateWithLifecycle()
    val install by app.updateInstall.state.collectAsStateWithLifecycle()
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().scrollingPage(padding, rememberScrollState(), LocalLayoutDirection.current)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageHeader(stringResource(R.string.updates_title), onBack)
            Text(stringResource(R.string.updates_installed, BuildConfig.VERSION_NAME))
            UpdateResultView(state, onAccess)
            if (state.result != UpdateResult.Debug) {
                OutlinedButton(onClick = { app.updates.check() }, enabled = !state.checking) {
                    Text(stringResource(R.string.updates_check))
                }
            }
            UpdateOffer(state, app)
            AnimatedContent(install, label = "update install", transitionSpec = { fadeIn() togetherWith fadeOut() }) {
                when {
                    it.busy -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    it.failed -> Text(
                        stringResource(R.string.updates_install_failed), color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
            }
            ReleaseLink()
        }
    }
}

@Composable
private fun UpdateResultView(state: UpdateState, onAccess: () -> Unit) {
    AnimatedContent(state.checking, label = "update check", transitionSpec = { fadeIn() togetherWith fadeOut() }) {
        if (it) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.updates_checking))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else UpdateResultText(state.result, onAccess)
    }
}

@Composable
private fun UpdateResultText(result: UpdateResult, onAccess: () -> Unit) {
    when (result) {
        is UpdateResult.Read -> if (result.release == null) {
            Text(stringResource(R.string.updates_none))
        } else Text(stringResource(R.string.updates_latest, result.release.version.toString()))
        is UpdateResult.Failed -> Text(stringResource(R.string.updates_failed), color = MaterialTheme.colorScheme.error)
        UpdateResult.Debug -> Text(stringResource(R.string.updates_debug))
        UpdateResult.SignIn, UpdateResult.NoAccess -> Column {
            Text(stringResource(R.string.updates_access_needed))
            TextButton(onAccess) { Text(stringResource(R.string.updates_open_access)) }
        }
    }
}

@Composable
private fun UpdateOffer(state: UpdateState, app: PreGlideApplication) {
    val release = (state.result as? UpdateResult.Read)?.release
    val installed = UpdateInstallation(checkNotNull(ReleaseVersion.parse(BuildConfig.VERSION_NAME)),
        BuildConfig.VERSION_CODE.toLong(), BuildConfig.FLAVOR)
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (release != null && updateIsNewer(release, installed)) {
            Text(stringResource(R.string.updates_available, release.version.toString()))
            AnimatedContent(
                state.download,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "update download",
            ) { download -> DownloadAction(download, release, app) }
        }
    }
}

@Composable
private fun DownloadAction(download: UpdateDownload, release: UpdateRelease, app: PreGlideApplication) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (download) {
            is UpdateDownload.Running -> LinearProgressIndicator(
                progress = { download.fraction }, modifier = Modifier.fillMaxWidth(),
            )
            is UpdateDownload.Ready -> InstallButton(download.file, app)
            else -> {
                if (download is UpdateDownload.Failed) {
                    Text(stringResource(R.string.updates_download_failed), color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = { app.updates.download(release) }) {
                    Text(stringResource(R.string.updates_download))
                }
            }
        }
    }
}

/** Return from Android's permission screen leads to the same confirmation as an already allowed install. */
@Composable
private fun InstallButton(file: File, app: PreGlideApplication) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        confirm = pending && context.packageManager.canRequestPackageInstalls()
        pending = false
    }
    val installing by app.updateInstall.state.collectAsStateWithLifecycle()
    Button(onClick = {
        if (context.packageManager.canRequestPackageInstalls()) confirm = true
        else {
            pending = true
            permission.launch(Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri(),
            ))
        }
    }, enabled = !installing.busy) { Text(stringResource(R.string.updates_install)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.updates_install)) },
            text = { Text(stringResource(R.string.updates_install_hint)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; app.updateInstall.install(file) }) {
                    Text(stringResource(R.string.updates_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.updates_cancel)) }
            },
        )
    }
}

@Composable
private fun ReleaseLink() {
    val links = LocalUriHandler.current
    UPDATE_RELEASES_URL?.let { url ->
        TextButton(onClick = { links.openUri(url) }) { Text(stringResource(R.string.updates_github)) }
    }
}

@Composable
internal fun UpdatesMenuEntry(onOpen: (SettingsPage) -> Unit) {
    PageEntry(
        R.drawable.ic_sync,
        stringResource(R.string.updates_title),
        stringResource(R.string.updates_hint),
        { onOpen(SettingsPage.Updates) },
    )
}
