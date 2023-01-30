package org.wordpress.android.ui.bloggingprompts

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.wordpress.android.fluxc.store.BloggingRemindersStore
import org.wordpress.android.ui.mysite.SelectedSiteRepository
import org.wordpress.android.ui.prefs.AppPrefsWrapper
import org.wordpress.android.util.DateUtils.isSameDay
import org.wordpress.android.util.config.BloggingPromptsEnhancementsFeatureConfig
import org.wordpress.android.util.config.BloggingPromptsFeatureConfig
import java.util.Date
import javax.inject.Inject

class BloggingPromptsSettingsHelper @Inject constructor(
    private val bloggingRemindersStore: BloggingRemindersStore,
    private val selectedSiteRepository: SelectedSiteRepository,
    private val appPrefsWrapper: AppPrefsWrapper,
    private val bloggingPromptsFeatureConfig: BloggingPromptsFeatureConfig,
    private val bloggingPromptsEnhancementsFeatureConfig: BloggingPromptsEnhancementsFeatureConfig,
) {
    fun getPromptsCardEnabledLiveData(
        siteId: Int
    ): LiveData<Boolean> = bloggingRemindersStore.bloggingRemindersModel(siteId)
        .asLiveData()
        .map { it.isPromptsCardEnabled }

    fun updatePromptsCardEnabledBlocking(siteId: Int, isEnabled: Boolean) = runBlocking {
        updatePromptsCardEnabled(siteId, isEnabled)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun updatePromptsCardEnabled(siteId: Int, isEnabled: Boolean) {
        val current = bloggingRemindersStore.bloggingRemindersModel(siteId).firstOrNull() ?: return
        bloggingRemindersStore.updateBloggingReminders(current.copy(isPromptsCardEnabled = isEnabled))
    }

    fun isPromptsFeatureAvailableBlocking(): Boolean = runBlocking { isPromptsFeatureAvailable() }

    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun isPromptsFeatureAvailable(): Boolean {
        val selectedSite = selectedSiteRepository.getSelectedSite() ?: return false
        val isPotentialBloggingSite = selectedSite.isPotentialBloggingSite

        return bloggingPromptsFeatureConfig.isEnabled() &&
                (isPotentialBloggingSite || isPromptIncludedInReminder(selectedSite.localId().value))
    }

    suspend fun isPromptsFeatureActive(): Boolean {
        val siteId = selectedSiteRepository.getSelectedSite()?.localId()?.value ?: return false

        // if the enhancements is turned off, consider the prompts user-enabled, otherwise check the user setting
        val isPromptsCardUserEnabled = !bloggingPromptsEnhancementsFeatureConfig.isEnabled() ||
                isPromptsCardEnabled(siteId)

        return isPromptsFeatureAvailable() && isPromptsCardUserEnabled && !isPromptSkippedForToday()
    }

    private fun isPromptSkippedForToday(): Boolean {
        val selectedSite = selectedSiteRepository.getSelectedSite() ?: return false

        val promptSkippedDate = appPrefsWrapper.getSkippedPromptDay(selectedSite.localId().value)
        return promptSkippedDate != null && isSameDay(promptSkippedDate, Date())
    }

    private suspend fun isPromptsCardEnabled(
        siteId: Int
    ): Boolean = bloggingRemindersStore
        .bloggingRemindersModel(siteId)
        .firstOrNull()
        ?.isPromptsCardEnabled == true

    private suspend fun isPromptIncludedInReminder(
        siteId: Int
    ): Boolean = bloggingRemindersStore
        .bloggingRemindersModel(siteId)
        .firstOrNull()
        ?.isPromptIncluded == true
}
