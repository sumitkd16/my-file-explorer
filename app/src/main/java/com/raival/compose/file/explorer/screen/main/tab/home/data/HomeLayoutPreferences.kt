package com.raival.compose.file.explorer.screen.main.tab.home.data

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import kotlinx.serialization.Serializable

object HomeSectionIds {
    // const val RECENT_FILES = "recent_files" // <-- REMOVED
    const val CATEGORIES = "categories"
    const val STORAGE = "storage"
    const val BOOKMARKS = "bookmarks"
    const val FAVORITES = "favorites" // <-- NEW
    const val PINNED_FILES = "pinned_files"
    // const val RECYCLE_BIN = "recycle_bin" // <-- REMOVED
    const val JUMP_TO_PATH = "jump_to_path"
}

fun getDefaultHomeLayout(minimalLayout: Boolean = false) = HomeLayout(
    listOf(
        // --- REMOVED RECENT_FILES SECTION ---

        HomeSectionConfig(
            id = HomeSectionIds.CATEGORIES,
            type = HomeSectionType.CATEGORIES,
            title = globalClass.getString(R.string.categories),
            isEnabled = !minimalLayout,
            order = 0 // <-- Re-ordered
        ),
        HomeSectionConfig(
            id = HomeSectionIds.STORAGE,
            type = HomeSectionType.STORAGE,
            title = globalClass.getString(R.string.storage),
            isEnabled = true,
            order = 1 // <-- Re-ordered
        ),
        HomeSectionConfig(
            id = HomeSectionIds.BOOKMARKS,
            type = HomeSectionType.BOOKMARKS,
            title = globalClass.getString(R.string.bookmarks),
            isEnabled = !minimalLayout,
            order = 2 // <-- Re-ordered
        ),
        // --- NEW FAVORITES SECTION ---
        HomeSectionConfig(
            id = HomeSectionIds.FAVORITES,
            type = HomeSectionType.FAVORITES,
            title = globalClass.getString(R.string.favorites),
            isEnabled = !minimalLayout,
            order = 3
        ),
        // --- END NEW ---
        HomeSectionConfig(
            id = HomeSectionIds.PINNED_FILES,
            type = HomeSectionType.PINNED_FILES,
            title = globalClass.getString(R.string.pinned_files),
            isEnabled = !minimalLayout,
            order = 4 // <-- Re-ordered
        ),

        // --- REMOVED RECYCLE_BIN SECTION ---

        HomeSectionConfig(
            id = HomeSectionIds.JUMP_TO_PATH,
            type = HomeSectionType.JUMP_TO_PATH,
            title = globalClass.getString(R.string.jump_to_path),
            isEnabled = !minimalLayout,
            order = 5 // <-- Re-ordered
        )
    )
)

@Serializable
data class HomeLayout(
    private val sections: List<HomeSectionConfig>
) {
    // Adds missing sections for backward compatibility with older saved layouts
    fun getSections(): List<HomeSectionConfig> {
        var modifiedSections = sections

        // Add Pinned Files if missing (for layouts saved before v1.3.2)
        if (modifiedSections.find { it.id == HomeSectionIds.PINNED_FILES } == null) {
            modifiedSections = modifiedSections.plus(
                HomeSectionConfig(
                    id = HomeSectionIds.PINNED_FILES,
                    type = HomeSectionType.PINNED_FILES,
                    title = globalClass.getString(R.string.pinned_files),
                    isEnabled = true,
                    order = modifiedSections.maxOfOrNull { it.order }?.plus(1) ?: 0
                )
            )
        }

        // --- NEW ---
        // Add Favorites if missing
        if (modifiedSections.find { it.id == HomeSectionIds.FAVORITES } == null) {
            modifiedSections = modifiedSections.plus(
                HomeSectionConfig(
                    id = HomeSectionIds.FAVORITES,
                    type = HomeSectionType.FAVORITES,
                    title = globalClass.getString(R.string.favorites),
                    isEnabled = true,
                    order = modifiedSections.maxOfOrNull { it.order }?.plus(1) ?: 0
                )
            )
        }
        // --- END NEW ---

        return modifiedSections
    }
}

@Serializable
data class HomeSectionConfig(
    val id: String,
    val type: HomeSectionType,
    val title: String,
    val isEnabled: Boolean,
    var order: Int
)

@Serializable
enum class HomeSectionType {
    // RECENT_FILES, // <-- REMOVED
    CATEGORIES,
    STORAGE,
    BOOKMARKS,
    FAVORITES, // <-- NEW
    // RECYCLE_BIN, // <-- REMOVED
    JUMP_TO_PATH,
    PINNED_FILES
}