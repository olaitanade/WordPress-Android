package org.wordpress.android.ui.bloggingreminders

import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import org.wordpress.android.fluxc.model.BloggingRemindersModel
import org.wordpress.android.fluxc.model.BloggingRemindersModel.Day
import org.wordpress.android.fluxc.store.BloggingRemindersStore
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.bloggingreminders.BloggingRemindersAnalyticsTracker.Source
import org.wordpress.android.ui.bloggingreminders.BloggingRemindersAnalyticsTracker.Source.BLOG_SETTINGS
import org.wordpress.android.ui.bloggingreminders.BloggingRemindersAnalyticsTracker.Source.PUBLISH_FLOW
import org.wordpress.android.ui.bloggingreminders.BloggingRemindersViewModel.Screen.EPILOGUE
import org.wordpress.android.ui.bloggingreminders.BloggingRemindersViewModel.Screen.PROLOGUE
import org.wordpress.android.ui.bloggingreminders.BloggingRemindersViewModel.Screen.PROLOGUE_SETTINGS
import org.wordpress.android.ui.bloggingreminders.BloggingRemindersViewModel.Screen.SELECTION
import org.wordpress.android.ui.utils.ListItemInteraction
import org.wordpress.android.ui.utils.UiString
import org.wordpress.android.util.merge
import org.wordpress.android.util.perform
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.ScopedViewModel
import org.wordpress.android.workers.reminder.ReminderConfig.WeeklyReminder
import org.wordpress.android.workers.reminder.ReminderScheduler
import java.time.DayOfWeek
import java.util.ArrayList
import javax.inject.Inject
import javax.inject.Named

@Suppress("TooManyFunctions")
class BloggingRemindersViewModel @Inject constructor(
    @Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher,
    private val bloggingRemindersManager: BloggingRemindersManager,
    private val bloggingRemindersStore: BloggingRemindersStore,
    private val prologueBuilder: PrologueBuilder,
    private val daySelectionBuilder: DaySelectionBuilder,
    private val epilogueBuilder: EpilogueBuilder,
    private val dayLabelUtils: DayLabelUtils,
    private val analyticsTracker: BloggingRemindersAnalyticsTracker,
    private val reminderScheduler: ReminderScheduler
) : ScopedViewModel(mainDispatcher) {
    private val _isBottomSheetShowing = MutableLiveData<Event<Boolean>>()
    val isBottomSheetShowing = _isBottomSheetShowing as LiveData<Event<Boolean>>
    private val _selectedScreen = MutableLiveData<Screen>()
    private val selectedScreen = _selectedScreen.perform { onScreenChanged(it) }
    private val _bloggingRemindersModel = MutableLiveData<BloggingRemindersModel>()
    private val _isFirstTimeFlow = MutableLiveData<Boolean>()
    val uiState: LiveData<UiState> = merge(
            selectedScreen,
            _bloggingRemindersModel,
            _isFirstTimeFlow
    ) { screen, bloggingRemindersModel, isFirstTimeFlow ->
        if (screen != null) {
            val uiItems = when (screen) {
                PROLOGUE -> prologueBuilder.buildUiItems()
                PROLOGUE_SETTINGS -> prologueBuilder.buildUiItemsForSettings()
                SELECTION -> daySelectionBuilder.buildSelection(bloggingRemindersModel, this::selectDay)
                EPILOGUE -> epilogueBuilder.buildUiItems(bloggingRemindersModel)
            }
            val primaryButton = when (screen) {
                PROLOGUE, PROLOGUE_SETTINGS -> prologueBuilder.buildPrimaryButton(
                        isFirstTimeFlow ?: false,
                        startDaySelection
                )
                SELECTION -> daySelectionBuilder.buildPrimaryButton(
                        bloggingRemindersModel,
                        isFirstTimeFlow == true,
                        this::showEpilogue
                )
                EPILOGUE -> epilogueBuilder.buildPrimaryButton(finish)
            }
            UiState(uiItems, primaryButton)
        } else {
            UiState(listOf())
        }
    }.distinctUntilChanged()

    private val startDaySelection: (isFirstTimeFlow: Boolean) -> Unit = { isFirstTimeFlow ->
        analyticsTracker.trackPrimaryButtonPressed(PROLOGUE)
        _isFirstTimeFlow.value = isFirstTimeFlow
        _selectedScreen.value = SELECTION
    }

    private val finish: () -> Unit = {
        analyticsTracker.trackPrimaryButtonPressed(EPILOGUE)
        _isBottomSheetShowing.value = Event(false)
    }

    private fun onScreenChanged(screen: Screen) {
        analyticsTracker.trackScreenShown(screen)
    }

    fun getSettingsState(siteId: Int): LiveData<UiString> {
        return bloggingRemindersStore.bloggingRemindersModel(siteId).map {
            dayLabelUtils.buildNTimesLabel(it)
        }.asLiveData(mainDispatcher)
    }

    fun showBottomSheet(siteId: Int, screen: Screen, source: Source) {
        analyticsTracker.setSite(siteId)
        analyticsTracker.trackFlowStart(source)
        if (screen == PROLOGUE) {
            bloggingRemindersManager.bloggingRemindersShown(siteId)
        } else {
            _isFirstTimeFlow.value = false
        }
        _isBottomSheetShowing.value = Event(true)
        _selectedScreen.value = screen
        launch {
            bloggingRemindersStore.bloggingRemindersModel(siteId).collect {
                _bloggingRemindersModel.value = it
            }
        }
    }

    fun selectDay(day: Day) {
        val currentState = _bloggingRemindersModel.value!!
        val enabledDays = currentState.enabledDays.toMutableSet()
        if (enabledDays.contains(day)) {
            enabledDays.remove(day)
        } else {
            enabledDays.add(day)
        }
        _bloggingRemindersModel.value = currentState.copy(enabledDays = enabledDays)
    }

    private fun showEpilogue(bloggingRemindersModel: BloggingRemindersModel?) {
        analyticsTracker.trackPrimaryButtonPressed(SELECTION)
        if (bloggingRemindersModel != null) {
            launch {
                bloggingRemindersStore.updateBloggingReminders(bloggingRemindersModel)
                val daysCount = bloggingRemindersModel.enabledDays.size
                if (daysCount > 0) {
                    reminderScheduler.schedule(bloggingRemindersModel.siteId, bloggingRemindersModel.toReminderConfig())
                    analyticsTracker.trackRemindersScheduled(daysCount)
                } else {
                    reminderScheduler.cancelBySiteId(bloggingRemindersModel.siteId)
                    analyticsTracker.trackRemindersCancelled()
                }
                _selectedScreen.value = EPILOGUE
            }
        }
    }

    fun saveState(outState: Bundle) {
        _selectedScreen.value?.let {
            outState.putSerializable(SELECTED_SCREEN, it)
        }
        _bloggingRemindersModel.value?.let { model ->
            outState.putInt(SITE_ID, model.siteId)
            outState.putStringArrayList(SELECTED_DAYS, ArrayList(model.enabledDays.map { it.name }))
        }
        _isFirstTimeFlow.value?.let {
            outState.putBoolean(IS_FIRST_TIME_FLOW, it)
        }
    }

    fun restoreState(state: Bundle) {
        state.getSerializable(SELECTED_SCREEN)?.let {
            _selectedScreen.value = it as Screen
        }
        val siteId = state.getInt(SITE_ID)
        if (siteId != 0) {
            val enabledDays = state.getStringArrayList(SELECTED_DAYS)?.map { Day.valueOf(it) }?.toSet() ?: setOf()
            _bloggingRemindersModel.value = BloggingRemindersModel(siteId, enabledDays)
        }
        _isFirstTimeFlow.value = state.getBoolean(IS_FIRST_TIME_FLOW)
    }

    fun onPostCreated(siteId: Int, isNewPost: Boolean?) {
        if (isNewPost == true && bloggingRemindersManager.shouldShowBloggingRemindersPrompt(siteId)) {
            showBottomSheet(siteId, PROLOGUE, PUBLISH_FLOW)
        }
    }

    fun onSettingsItemClicked(siteId: Int) {
        launch {
                val screen = if (bloggingRemindersStore.hasModifiedBloggingReminders(siteId)) {
                    SELECTION
                } else {
                    PROLOGUE_SETTINGS
                }
            showBottomSheet(siteId, screen, BLOG_SETTINGS)
        }
    }

    fun onBottomSheetDismissed() {
        when (val screen = selectedScreen.value) {
            PROLOGUE,
            PROLOGUE_SETTINGS,
            SELECTION -> analyticsTracker.trackFlowDismissed(screen)
            EPILOGUE -> analyticsTracker.trackFlowCompleted()
        }
    }

    private fun BloggingRemindersModel.toReminderConfig() =
            WeeklyReminder(enabledDays.map { DayOfWeek.of(it.ordinal + 1) }.toSet())

    enum class Screen(val trackingName: String) {
        PROLOGUE("main"),
        PROLOGUE_SETTINGS("main"),
        SELECTION("day_picker"),
        EPILOGUE("all_set")
    }

    data class UiState(val uiItems: List<BloggingRemindersItem>, val primaryButton: PrimaryButton? = null) {
        data class PrimaryButton(val text: UiString, val enabled: Boolean, val onClick: ListItemInteraction)
    }

    companion object {
        private const val SELECTED_SCREEN = "key_shown_screen"
        private const val SELECTED_DAYS = "key_selected_days"
        private const val IS_FIRST_TIME_FLOW = "is_first_time_flow"
        private const val SITE_ID = "key_site_id"
    }
}
