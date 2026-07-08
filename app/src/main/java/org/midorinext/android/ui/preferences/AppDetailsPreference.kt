package org.midorinext.android.ui.preferences

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import org.midorinext.android.R
import org.midorinext.android.ui.preferences.widgets.PreferenceRow
import org.midorinext.android.ui.theme.ActionBlue300
import org.midorinext.android.ui.widgets.MidoriIconOnBackground
import org.mozilla.geckoview.BuildConfig

@Composable
fun AppDetailsPreference() {
    val context = LocalContext.current

    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, 0) // No alternative from compat libraries yet
    }

    PreferenceRow(
        label = R.string.app_name,
        description = stringResource(
            R.string.qwant_details_description,
            packageInfo.versionName ?: "",
            PackageInfoCompat.getLongVersionCode(packageInfo).toString(),
            BuildConfig.MOZ_APP_VERSION
        ),
        trailing = { MidoriIconOnBackground(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(28.dp))
        },
        onClicked = {}
    )
}