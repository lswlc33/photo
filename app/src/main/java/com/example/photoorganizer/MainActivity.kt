package com.example.photoorganizer

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.photoorganizer.ui.components.MediaThumbnailCache

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(
                enabled = true,
                parent = null,
            )
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner,
            ) {
                PhotoOrganizerApp()
            }
        }
    }

    /**
     * The decoded-thumbnail cache holds the largest allocations in the process and
     * every entry is reproducible from MediaStore, so it should be the first thing
     * to go under pressure rather than something the platform reclaims by killing
     * the process.
     *
     * Only the two levels that are not deprecated are worth branching on -
     * everything below `TRIM_MEMORY_UI_HIDDEN` stopped being delivered in API 34,
     * so the lighter branch is what a pre-34 device sends and API 34+ never does.
     * `onLowMemory` is not overridden for the same reason: it is deprecated and
     * never called from API 34 on.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            MediaThumbnailCache.evictAll()
        } else {
            MediaThumbnailCache.trimPreviews()
        }
    }
}
