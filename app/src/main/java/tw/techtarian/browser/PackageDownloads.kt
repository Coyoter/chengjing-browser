package tw.techtarian.browser

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal object PackageDownloads {
    const val MIME="application/vnd.android.package-archive"
    fun isPackage(item:DownloadItem)=item.mime.equals(MIME,true)||item.title.endsWith(".apk",true)
    fun allowed(context:Context)=runCatching{context.packageManager.canRequestPackageInstalls()}.getOrDefault(false)
    fun settingsIntent(context:Context)=Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))
    fun installerIntent(uri:Uri):Intent {
        require(uri.scheme=="content")
        @Suppress("DEPRECATION")
        return Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=ClipData.newRawUri("APK",uri)}
    }
    fun contentUri(context:Context,item:DownloadItem):Uri {
        val direct=item.contentUri?.let(Uri::parse)?.takeIf{it.scheme=="content"}
        val uri=direct?:context.getSystemService(DownloadManager::class.java).getUriForDownloadedFile(item.id)
        require(uri?.scheme=="content")
        context.contentResolver.openFileDescriptor(uri,"r")?.use{}?:error("Unavailable APK")
        return uri
    }
}
