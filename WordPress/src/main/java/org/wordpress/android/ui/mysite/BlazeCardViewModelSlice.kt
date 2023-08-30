package org.wordpress.android.ui.mysite

import androidx.lifecycle.MutableLiveData
import org.wordpress.android.analytics.AnalyticsTracker
import org.wordpress.android.fluxc.model.blaze.BlazeCampaignModel
import org.wordpress.android.ui.blaze.BlazeFeatureUtils
import org.wordpress.android.ui.blaze.BlazeFlowSource
import org.wordpress.android.ui.blaze.blazecampaigns.campaigndetail.CampaignDetailPageSource
import org.wordpress.android.ui.blaze.blazecampaigns.campaignlisting.CampaignListingPageSource
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.BlazeCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.BlazeCardBuilderParams.CampaignWithBlazeCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteCardAndItemBuilderParams.BlazeCardBuilderParams.PromoteWithBlazeCardBuilderParams
import org.wordpress.android.ui.mysite.MySiteUiState.PartialState.BlazeCardUpdate
import org.wordpress.android.ui.mysite.cards.dashboard.CardsTracker
import org.wordpress.android.viewmodel.Event
import javax.inject.Inject

class BlazeCardViewModelSlice @Inject constructor(
    private val blazeFeatureUtils: BlazeFeatureUtils,
    private val selectedSiteRepository: SelectedSiteRepository,
    private val cardsTracker: CardsTracker
) {
    private val _onNavigation = MutableLiveData<Event<SiteNavigationAction>>()
    val onNavigation = _onNavigation

    private val _refresh = MutableLiveData<Event<Boolean>>()
    val refresh = _refresh

    fun isSiteBlazeEligible() = blazeFeatureUtils.isSiteBlazeEligible(selectedSiteRepository.getSelectedSite()!!)

    fun getBlazeCardBuilderParams(blazeCardUpdate: BlazeCardUpdate?): BlazeCardBuilderParams? {
        return blazeCardUpdate?.let {
            if (it.blazeEligible) {
                it.campaign?.let { campaign ->
                    getCampaignWithBlazeCardBuilderParams(campaign)
                } ?: getPromoteWithBlazeCardBuilderParams()
            } else null
        }
    }

    private fun getCampaignWithBlazeCardBuilderParams(campaign: BlazeCampaignModel) =
        CampaignWithBlazeCardBuilderParams(
            campaign = campaign,
            onCreateCampaignClick = this::onCreateCampaignClick,
            onCampaignClick = this::onCampaignClick,
            onCardClick = this::onCampaignsCardClick,
            moreMenuParams = CampaignWithBlazeCardBuilderParams.MoreMenuParams(
                viewAllCampaignsItemClick = this::onViewAllCampaignsClick,
                onLearnMoreClick = this::onCampaignCardLearnMoreClick,
                onHideThisCardItemClick = this::onCampaignCardHideMenuItemClick,
                onMoreMenuClick = this::onCampaignCardMoreMenuClick
            )
        )

    private fun onViewAllCampaignsClick() {
        cardsTracker.trackCardMoreMenuItemClicked(
            CardsTracker.Type.BLAZE_CAMPAIGNS.label,
            CampaignCardMenuItem.VIEW_ALL_CAMPAIGNS.label
        )
        _onNavigation.value =
            Event(SiteNavigationAction.OpenCampaignListingPage(CampaignListingPageSource.DASHBOARD_CARD))
    }

    private fun onCampaignCardLearnMoreClick() {
        cardsTracker.trackCardMoreMenuItemClicked(
            CardsTracker.Type.BLAZE_CAMPAIGNS.label,
            CampaignCardMenuItem.LEARN_MORE.label
        )
        onLearnMoreClick()
    }

    private fun onCampaignCardHideMenuItemClick() {
        // todo implement the tracking
        onHideCardClick()
    }

    private fun onCampaignCardMoreMenuClick() {
        cardsTracker.trackCardMoreMenuClicked(CardsTracker.Type.BLAZE_CAMPAIGNS.label)
        blazeFeatureUtils.track(
            AnalyticsTracker.Stat.BLAZE_ENTRY_POINT_MENU_ACCESSED,
            BlazeFlowSource.DASHBOARD_CARD
        )
    }

    private fun getPromoteWithBlazeCardBuilderParams() =
        PromoteWithBlazeCardBuilderParams(
            onClick = this::onPromoteWithBlazeCardClick,
            moreMenuParams = PromoteWithBlazeCardBuilderParams.MoreMenuParams(
                onLearnMoreClick = this::onPromoteCardLearnMoreClick,
                onHideThisCardItemClick = this::onPromoteCardHideMenuItemClick,
                onMoreMenuClick = this::onPromoteCardMoreMenuClick
            )
        )


    private fun onPromoteCardLearnMoreClick() {
        // todo implement the navigation and tracking
        onLearnMoreClick()
    }

    private fun onLearnMoreClick() {
        _onNavigation.value =
            Event(
                SiteNavigationAction.OpenPromoteWithBlazeOverlay(
                    source = BlazeFlowSource.DASHBOARD_CARD,
                    shouldShowBlazeOverlay = true
                )
            )
    }

    private fun onPromoteCardHideMenuItemClick() {
        blazeFeatureUtils.track(
            AnalyticsTracker.Stat.BLAZE_ENTRY_POINT_HIDE_TAPPED,
            BlazeFlowSource.DASHBOARD_CARD
        )
        onHideCardClick()
    }

    private fun onHideCardClick() {
        selectedSiteRepository.getSelectedSite()?.let {
            blazeFeatureUtils.hideBlazeCard(it.siteId)
        }
        _refresh.value = Event(true)
    }

    private fun onPromoteCardMoreMenuClick() {
        blazeFeatureUtils.track(
            AnalyticsTracker.Stat.BLAZE_ENTRY_POINT_MENU_ACCESSED,
            BlazeFlowSource.DASHBOARD_CARD
        )
    }

    private fun onCreateCampaignClick() {
        blazeFeatureUtils.trackEntryPointTapped(BlazeFlowSource.DASHBOARD_CARD)
        _onNavigation.value =
            Event(SiteNavigationAction.OpenPromoteWithBlazeOverlay(source = BlazeFlowSource.DASHBOARD_CARD))
    }

    private fun onCampaignClick(campaignId: Int) {
        _onNavigation.value =
            Event(SiteNavigationAction.OpenCampaignDetailPage(campaignId, CampaignDetailPageSource.DASHBOARD_CARD))
    }

    private fun onCampaignsCardClick() {
        _onNavigation.value =
            Event(SiteNavigationAction.OpenCampaignListingPage(CampaignListingPageSource.DASHBOARD_CARD))
    }

    private fun onPromoteWithBlazeCardClick() {
        blazeFeatureUtils.trackEntryPointTapped(BlazeFlowSource.DASHBOARD_CARD)
        _onNavigation.value =
            Event(SiteNavigationAction.OpenPromoteWithBlazeOverlay(source = BlazeFlowSource.DASHBOARD_CARD))
    }

    fun onBlazeMenuItemClick(): SiteNavigationAction {
        blazeFeatureUtils.trackEntryPointTapped(BlazeFlowSource.MENU_ITEM)
        if (blazeFeatureUtils.shouldShowBlazeCampaigns()) {
            return SiteNavigationAction.OpenCampaignListingPage(CampaignListingPageSource.MENU_ITEM)
        }
        return SiteNavigationAction.OpenPromoteWithBlazeOverlay(BlazeFlowSource.MENU_ITEM)
    }
}

enum class CampaignCardMenuItem(val label: String) {
    VIEW_ALL_CAMPAIGNS("view_stats"),
    LEARN_MORE("learn_more"),
    HIDE_THIS("hide_this")
}

