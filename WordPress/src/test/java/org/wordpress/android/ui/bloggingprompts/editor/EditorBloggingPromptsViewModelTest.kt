package org.wordpress.android.ui.bloggingprompts.editor

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.TEST_DISPATCHER
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.bloggingprompts.BloggingPromptModel
import org.wordpress.android.fluxc.store.bloggingprompts.BloggingPromptsStore
import org.wordpress.android.fluxc.store.bloggingprompts.BloggingPromptsStore.BloggingPromptsResult
import org.wordpress.android.test
import org.wordpress.android.ui.posts.EditorBloggingPromptsViewModel
import org.wordpress.android.ui.posts.EditorBloggingPromptsViewModel.EditorLoadedPrompt
import java.util.Date

@InternalCoroutinesApi
class EditorBloggingPromptsViewModelTest : BaseUnitTest() {
    @Mock lateinit var siteModel: SiteModel

    private lateinit var viewModel: EditorBloggingPromptsViewModel
    private var loadedPrompt: EditorLoadedPrompt? = null

    private val bloggingPrompt = BloggingPromptsResult(
            model = BloggingPromptModel(
                    id = 123,
                    text = "title",
                    title = "",
                    content = "content",
                    date = Date(),
                    isAnswered = false,
                    attribution = "",
                    respondentsCount = 5,
                    respondentsAvatarUrls = listOf()
            )
    )

    private val bloggingPromptsStore: BloggingPromptsStore = mock {
        onBlocking { getPromptById(any(), any()) } doReturn flowOf(bloggingPrompt)
    }

    @Before
    fun setUp() {
        viewModel = EditorBloggingPromptsViewModel(
                bloggingPromptsStore,
                TEST_DISPATCHER
        )

        viewModel.onBloggingPromptLoaded.observeForever {
            it.applyIfNotHandled {
                loadedPrompt = this
            }
        }
    }

    @Test
    fun `starting VM fetches a prompt and posts it to onBloggingPromptLoaded`() = test {
        viewModel.start(siteModel, 123)

        assertThat(loadedPrompt?.content).isEqualTo(bloggingPrompt.model?.content)
        assertThat(loadedPrompt?.promptId).isEqualTo(bloggingPrompt.model?.id)

        verify(bloggingPromptsStore, times(1)).getPromptById(any(), any())
    }

    @Test
    fun `should NOT execute start method if prompt ID is less than 0`() = test {
        viewModel.start(siteModel, -1)
        verify(bloggingPromptsStore, times(0)).getPromptById(any(), any())
    }
}
