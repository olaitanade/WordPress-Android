package org.wordpress.android.fluxc.network.wporg.plugin;

import android.support.annotation.NonNull;

import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response.Listener;

import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.generated.PluginActionBuilder;
import org.wordpress.android.fluxc.generated.endpoint.WPORGAPI;
import org.wordpress.android.fluxc.model.WPOrgPluginModel;
import org.wordpress.android.fluxc.network.BaseRequest.BaseErrorListener;
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError;
import org.wordpress.android.fluxc.network.UserAgent;
import org.wordpress.android.fluxc.network.wporg.BaseWPOrgAPIClient;
import org.wordpress.android.fluxc.network.wporg.WPOrgAPIGsonRequest;
import org.wordpress.android.fluxc.store.PluginStore.FetchWPOrgPluginError;
import org.wordpress.android.fluxc.store.PluginStore.FetchWPOrgPluginErrorType;
import org.wordpress.android.fluxc.store.PluginStore.FetchedWPOrgPluginPayload;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PluginWPOrgClient extends BaseWPOrgAPIClient {
    private final Dispatcher mDispatcher;

    @Inject
    public PluginWPOrgClient(Dispatcher dispatcher, RequestQueue requestQueue, UserAgent userAgent) {
        super(dispatcher, requestQueue, userAgent);
        mDispatcher = dispatcher;
    }

    public void fetchWPOrgPlugin(String pluginSlug) {
        String url = WPORGAPI.plugins.info.version("1.0").slug(pluginSlug).getUrl();
        Map<String, String> params = new HashMap<>();
        params.put("fields", "icons");
        final WPOrgAPIGsonRequest<WPOrgPluginResponse> request =
                new WPOrgAPIGsonRequest<>(Method.GET, url, params, null, WPOrgPluginResponse.class,
                        new Listener<WPOrgPluginResponse>() {
                            @Override
                            public void onResponse(WPOrgPluginResponse response) {
                                if (response == null) {
                                    FetchWPOrgPluginError error = new FetchWPOrgPluginError(
                                            FetchWPOrgPluginErrorType.EMPTY_RESPONSE);
                                    mDispatcher.dispatch(PluginActionBuilder.newFetchedPluginInfoAction(
                                            new FetchedWPOrgPluginPayload(error)));
                                    return;
                                }
                                WPOrgPluginModel wpOrgPluginModel = wpOrgPluginFromResponse(response);
                                FetchedWPOrgPluginPayload payload = new FetchedWPOrgPluginPayload(wpOrgPluginModel);
                                mDispatcher.dispatch(PluginActionBuilder.newFetchedPluginInfoAction(payload));
                            }
                        },
                        new BaseErrorListener() {
                            @Override
                            public void onErrorResponse(@NonNull BaseNetworkError networkError) {
                                FetchWPOrgPluginError error = new FetchWPOrgPluginError(
                                        FetchWPOrgPluginErrorType.GENERIC_ERROR);
                                mDispatcher.dispatch(PluginActionBuilder.newFetchedPluginInfoAction(
                                        new FetchedWPOrgPluginPayload(error)));
                            }
                        }
                );
        add(request);
    }

    private WPOrgPluginModel wpOrgPluginFromResponse(WPOrgPluginResponse response) {
        WPOrgPluginModel wpOrgPluginModel = new WPOrgPluginModel();
        wpOrgPluginModel.setName(response.name);
        wpOrgPluginModel.setRating(response.rating);
        wpOrgPluginModel.setSlug(response.slug);
        wpOrgPluginModel.setVersion(response.version);
        wpOrgPluginModel.setIcon(response.icon);
        return wpOrgPluginModel;
    }
}
