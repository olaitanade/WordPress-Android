package org.wordpress.android.fluxc.network.rest.wpcom.whatsnew

import android.content.Context
import com.android.volley.RequestQueue
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.endpoint.WPCOMV2
import org.wordpress.android.fluxc.model.whatsnew.WhatsNewAnnounceModel
import org.wordpress.android.fluxc.model.whatsnew.WhatsNewAnnounceModel.Feature
import org.wordpress.android.fluxc.network.Response
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequestBuilder.Response.Success
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.whatsnew.WhatsNewRestClient.WhatsNewResponse.Announce
import org.wordpress.android.fluxc.store.WhatsNewStore.WhatsNewFetchedPayload
import javax.inject.Singleton

@Singleton
class WhatsNewRestClient constructor(
    dispatcher: Dispatcher,
    private val wpComGsonRequestBuilder: WPComGsonRequestBuilder,
    appContext: Context?,
    requestQueue: RequestQueue,
    accessToken: AccessToken,
    userAgent: UserAgent
) : BaseWPComRestClient(appContext, dispatcher, requestQueue, accessToken, userAgent) {
    suspend fun fetchWhatsNew(versionCode: String): WhatsNewFetchedPayload {
        val url = WPCOMV2.whats_new.mobile.url

        val params = mapOf(
                "client" to "android",
                "version" to versionCode
        )

        val response = wpComGsonRequestBuilder.syncGetRequest(
                this,
                url,
                params,
                WhatsNewResponse::class.java,
                enableCaching = false,
                forced = true
        )
        return when (response) {
            is Success -> {
                val announcements = response.data.announcements

                buildWhatsNewPayload(announcements)
            }
            is WPComGsonRequestBuilder.Response.Error -> {
                val payload = WhatsNewFetchedPayload()
                payload.error = response.error
                payload
            }
        }
    }

    private fun buildWhatsNewPayload(
        announcements: List<Announce>?
    ): WhatsNewFetchedPayload {
        return WhatsNewFetchedPayload(announcements?.map { announce ->
            WhatsNewAnnounceModel(
                    appVersionName = announce.appVersionName,
                    announcementVersion = announce.announcementVersion,
                    minimumAppVersionCode = announce.minimumAppVersionCode,
                    detailsUrl = announce.detailsUrl,
                    isLocalized = announce.isLocalized,
                    responseLocale = announce.responseLocale,
                    features = announce.features.map {
                        Feature(
                                title = it.title,
                                subtitle = it.subtitle,
                                iconBase64 = it.iconBase64,
                                iconUrl = it.iconUrl
                        )
                    }
            )
        })
    }

    data class WhatsNewResponse(
        val announcements: List<Announce>?
    ) : Response {
        data class Announce(
            val appVersionName: String,
            val announcementVersion: Int,
            val minimumAppVersionCode: Int,
            val detailsUrl: String,
            val isLocalized: Boolean,
            val responseLocale: String,
            val features: List<Feature>
        )

        data class Feature(
            val title: String,
            val subtitle: String,
            val iconBase64: String,
            val iconUrl: String
        )
    }
}
