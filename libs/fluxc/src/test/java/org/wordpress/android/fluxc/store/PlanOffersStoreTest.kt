package org.wordpress.android.fluxc.store

import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.PlansAction
import org.wordpress.android.fluxc.generated.PlansActionBuilder
import org.wordpress.android.fluxc.model.plans.PlanOfferModel
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError
import org.wordpress.android.fluxc.network.BaseRequest.GenericErrorType.NETWORK_ERROR
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.planoffers.PLAN_OFFERS_MODELS
import org.wordpress.android.fluxc.network.rest.wpcom.planoffers.PlanOffersRestClient
import org.wordpress.android.fluxc.persistence.PlanOffersSqlUtils
import org.wordpress.android.fluxc.store.PlanOffersStore.PlanOffersErrorType.GENERIC_ERROR
import org.wordpress.android.fluxc.store.PlanOffersStore.PlansFetchError
import org.wordpress.android.fluxc.store.PlanOffersStore.PlanOffersFetchedPayload
import org.wordpress.android.fluxc.test

@RunWith(MockitoJUnitRunner::class)
class PlanOffersStoreTest {
    @Mock private lateinit var planOffersRestClient: PlanOffersRestClient
    @Mock private lateinit var planOffersSqlUtils: PlanOffersSqlUtils
    @Mock private lateinit var dispatcher: Dispatcher
    private lateinit var planOffersStore: PlanOffersStore

    @ExperimentalCoroutinesApi
    @Before
    fun setUp() {
        planOffersStore = PlanOffersStore(planOffersRestClient, planOffersSqlUtils, Unconfined, dispatcher)
    }

    @Test
    fun fetchPlans() = test {
        initRestClient(PLAN_OFFERS_MODELS)

        val action = PlansActionBuilder.generateNoPayloadAction(PlansAction.FETCH_PLANS)

        planOffersStore.onAction(action)

        verify(planOffersRestClient).fetchPlanOffers()
        verify(planOffersSqlUtils).storePlanOffers(PLAN_OFFERS_MODELS)

        val expectedEvent = PlanOffersStore.OnPlanOffersFetched(PLAN_OFFERS_MODELS)
        verify(dispatcher).emitChange(eq(expectedEvent))
    }

    @Test
    fun fetchCachedPlansAfterError() = test {
        initRestClient(PLAN_OFFERS_MODELS)
        val action = PlansActionBuilder.generateNoPayloadAction(PlansAction.FETCH_PLANS)
        planOffersStore.onAction(action)

        val error = WPComGsonNetworkError(BaseNetworkError(NETWORK_ERROR))
        error.message = "NETWORK_ERROR"
        // tell rest client to return error and no plan offers
        initRestClient(error = error)

        val expectedEventWithoutError = PlanOffersStore.OnPlanOffersFetched(PLAN_OFFERS_MODELS)
        verify(dispatcher, times(1)).emitChange(eq(expectedEventWithoutError))

        planOffersStore.onAction(action)

        verify(planOffersRestClient, times(2)).fetchPlanOffers()

        // plan offers should not be stored on error
        verify(planOffersSqlUtils, times(1)).storePlanOffers(PLAN_OFFERS_MODELS)

        val expectedEventWithError = PlanOffersStore.OnPlanOffersFetched(
                PLAN_OFFERS_MODELS,
                PlansFetchError(GENERIC_ERROR, "NETWORK_ERROR")
        )
        verify(dispatcher, times(1)).emitChange(eq(expectedEventWithError))
    }

    private suspend fun initRestClient(
        planOffers: List<PlanOfferModel>? = null,
        error: WPComGsonNetworkError? = null
    ) {
        val payload = PlanOffersFetchedPayload(planOffers)

        if (error != null) {
            payload.error = error
        }

        whenever(planOffersRestClient.fetchPlanOffers()).thenReturn(payload)
        whenever(planOffersSqlUtils.getPlanOffers()).thenReturn(PLAN_OFFERS_MODELS)
    }
}
