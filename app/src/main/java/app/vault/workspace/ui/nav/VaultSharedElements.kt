package app.vault.workspace.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared-element / shared-bounds keys for library ↔ viewer Motion Phase 2.
 *
 * Thumbnail morph uses [thumb]; optional title key reserved for a later polish pass.
 *
 * Limitation: when [exitViewer] restores system bars before [popBackStack], the shared
 * morph still runs under Navigation Compose 2.8+, but predictive-back *gesture scrub*
 * of the shared element is best-effort — custom immersive restore + BackHandler can
 * short-circuit a fully scrubbable shared transition on some API levels.
 */
object VaultSharedKeys {
    fun thumb(itemId: String): String = "vault-item-thumb-$itemId"
    fun title(itemId: String): String = "vault-item-title-$itemId"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.vaultSharedThumb(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    itemId: String,
    /** Prefer sharedBounds when source/target visuals differ (e.g. PDF thumb → pager). */
    useBounds: Boolean = false,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    with(sharedTransitionScope) {
        val state = rememberSharedContentState(key = VaultSharedKeys.thumb(itemId))
        return if (useBounds) {
            this@vaultSharedThumb.sharedBounds(
                state,
                animatedVisibilityScope,
            )
        } else {
            this@vaultSharedThumb.sharedElement(
                state,
                animatedVisibilityScope,
            )
        }
    }
}
