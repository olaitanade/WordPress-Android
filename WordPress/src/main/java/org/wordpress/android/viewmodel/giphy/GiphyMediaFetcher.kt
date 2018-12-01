package org.wordpress.android.viewmodel.giphy

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.coroutineScope
import kotlinx.coroutines.experimental.yield
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.MediaActionBuilder
import org.wordpress.android.fluxc.model.MediaModel
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.MediaStore
import org.wordpress.android.util.FluxCUtils
import org.wordpress.android.util.WPMediaUtils
import javax.inject.Inject

class GiphyMediaFetcher @Inject constructor(
    private val context: Context,
    private val mediaStore: MediaStore,
    private val dispatcher: Dispatcher
) {
    /**
     * Inherits the [CoroutineScope] from the call site and traps all exceptions from launched coroutines inside this
     * [coroutineScope]. All coroutines executed under this [coroutineScope] will be cancelled if one of them throws
     * an exception.
     *
     * There is no need to log the [Exception] thrown by this method because the underlying methods already do that.
     */
    @Throws
    suspend fun fetchAndSave(uris: List<Uri>, site: SiteModel): List<MediaModel> = coroutineScope {
        // Execute [fetchAndSave] for all uris first so that they are queued and executed in the background. We'll
        // call `await()` once they are queued.
        return@coroutineScope uris.map { fetchAndSave(scope = this, uri = it, site = site) }
                .map { it.await() }
    }

    private fun fetchAndSave(scope: CoroutineScope, uri: Uri, site: SiteModel) = scope.async(Dispatchers.IO) {
        // No need to log the Exception here. The underlying method that is used, [MediaUtils.downloadExternalMedia]
        // already logs any errors.
        val downloadedUri = WPMediaUtils.fetchMedia(context, uri) ?: throw Exception("Failed to download the image.")

        // Exit if the parent coroutine has already been cancelled
        yield()

        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)

        val mediaModel = FluxCUtils.mediaModelFromLocalUri(context, downloadedUri, mimeType, mediaStore, site.id)
        dispatcher.dispatch(MediaActionBuilder.newUpdateMediaAction(mediaModel))

        return@async mediaModel
    }
}
