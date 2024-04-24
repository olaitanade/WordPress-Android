package org.wordpress.android.ui.posts

import org.wordpress.android.fluxc.model.PostModel
import org.wordpress.android.fluxc.model.post.PostStatus
import org.wordpress.android.fluxc.store.PostStore.RemotePostPayload
import org.wordpress.android.util.DateTimeUtilsWrapper
import org.wordpress.android.util.config.PostConflictResolutionFeatureConfig
import javax.inject.Inject

class PostConflictResolutionFeatureUtils @Inject constructor(
    private val postConflictResolutionFeatureConfig: PostConflictResolutionFeatureConfig,
    private val dateTimeUtilsWrapper: DateTimeUtilsWrapper
) {
    fun isPostConflictResolutionEnabled(): Boolean {
        return postConflictResolutionFeatureConfig.isEnabled()
    }

    /**
     * This helper function aids in post-conflict resolution. When attempting to edit a post,
     * sending the "if_not_modified_since" to the backend will trigger a 409 error if a newer version
     * has already been uploaded from another device. This functionality should be encapsulated
     * by the SYNC_PUBLISHING feature flag. The function is used to generate the final RemotePostPayload
     * that is sent to the backend through PostActionBuilder.newPushPostAction(). By setting the
     * shouldSkipConflictResolutionCheck = true, "if_not_modified_since" is not sent to server and the post overwrites
     * the remote version.
     */
    fun getRemotePostPayloadForPush(payload: RemotePostPayload): RemotePostPayload {
        if (isPostConflictResolutionEnabled()) {
            setLastModifiedForConflictResolution(payload)
        } else {
            payload.shouldSkipConflictResolutionCheck = true
        }
        return payload
    }

    private fun setLastModifiedForConflictResolution(payload: RemotePostPayload) {
        payload.post?.let { post ->
            payload.lastModifiedForConflictResolution = if (shouldUpdateLastModified(post)) {
                dateTimeUtilsWrapper.currentTimeInIso8601()
                //post.dateLocallyChanged
            } else {
                post.lastModified
            }
        }
    }

    private fun shouldUpdateLastModified(post: PostModel): Boolean {
        return post.status == PostStatus.SCHEDULED.toString() && post.remotePostId > 0 && post.lastModified == post.dateCreated
    }
}
