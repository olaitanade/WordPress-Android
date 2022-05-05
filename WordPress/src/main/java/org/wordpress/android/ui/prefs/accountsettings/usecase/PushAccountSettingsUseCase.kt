package org.wordpress.android.ui.prefs.accountsettings.usecase

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.BACKGROUND
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.generated.AccountActionBuilder
import org.wordpress.android.fluxc.store.AccountStore.OnAccountChanged
import org.wordpress.android.fluxc.store.AccountStore.PushAccountSettingsPayload
import org.wordpress.android.modules.IO_THREAD
import org.wordpress.android.ui.utils.ContinuationWrapperWithConcurrency
import javax.inject.Inject
import javax.inject.Named

class PushAccountSettingsUseCase @Inject constructor(
    private val dispatcher: Dispatcher,
    private val continuationWrapperWithConcurrency: ContinuationWrapperWithConcurrency<OnAccountChanged>,
    @Named(IO_THREAD) private val ioDispatcher: CoroutineDispatcher
) : PushAccountSettingsInteractor {

    init {
        dispatcher.register(this@PushAccountSettingsUseCase)
    }

    override suspend fun updatePrimaryBlog(blogId: String): OnAccountChanged {
        Log.d("ACCOUNT","updatePrimaryBlog")
        val addPayload: (PushAccountSettingsPayload) -> Unit = { it.params["primary_site_ID"] = blogId }
        return updateAccountSettings(addPayload)
    }

    override suspend fun cancelPendingEmailChange(): OnAccountChanged {
        Log.d("ACCOUNT","cancelPendingEmailChange")
        val addPayload: (PushAccountSettingsPayload) -> Unit = { it.params["user_email_change_pending"] = "false" }
        return updateAccountSettings(addPayload)
    }

    override suspend fun updateEmail(newEmail: String): OnAccountChanged {
        Log.d("ACCOUNT","updateEmail")
        val addPayload: (PushAccountSettingsPayload) -> Unit = { it.params["user_email"] = newEmail }
        return updateAccountSettings(addPayload)
    }

    override suspend fun updateWebAddress(newWebAddress: String): OnAccountChanged {
        Log.d("ACCOUNT","updateWebAddress")
        val addPayload: (PushAccountSettingsPayload) -> Unit = { it.params["user_URL"] = newWebAddress }
        return updateAccountSettings(addPayload)
    }

    override suspend fun updatePassword(newPassword: String): OnAccountChanged {
        Log.d("ACCOUNT","updatePassword")
        val addPayload: (PushAccountSettingsPayload) -> Unit = { it.params["password"] = newPassword }
        return updateAccountSettings(addPayload)
    }

    private suspend fun updateAccountSettings(addPayload: (PushAccountSettingsPayload) -> Unit): OnAccountChanged =
            withContext(ioDispatcher) {
                continuationWrapperWithConcurrency.suspendCoroutine {
                    val payload = PushAccountSettingsPayload()
                    payload.params = HashMap()
                    addPayload(payload)
                    dispatcher.dispatch(AccountActionBuilder.newPushSettingsAction(payload))
                }
            }

    @Subscribe(threadMode = BACKGROUND)
    fun onAccountChanged(event: OnAccountChanged) {
        Log.d("ACCOUNT","onAccountChanged")
        continuationWrapperWithConcurrency.continueWith( event)
        //if(!continuationWrapperWithConcurrency.isWaiting){ eventBusWrapper.unregister(this) }
    }
}

interface PushAccountSettingsInteractor{
    suspend fun updatePrimaryBlog(blogId: String): OnAccountChanged
    suspend fun cancelPendingEmailChange(): OnAccountChanged
    suspend fun updateEmail(newEmail: String): OnAccountChanged
    suspend fun updateWebAddress(newWebAddress: String): OnAccountChanged
    suspend fun updatePassword(newPassword: String): OnAccountChanged
}
