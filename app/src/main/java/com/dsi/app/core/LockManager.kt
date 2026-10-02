package com.dsi.app.core

/**
 * Tracks whether a system picker or external camera activity is currently open.
 * Prevents the app from prematurely locking itself when the user switches to
 * the camera or photo gallery to attach an image.
 */
object LockManager {
    @Volatile
    var isPickerActive: Boolean = false

    @Volatile
    var isChatOpen: Boolean = false
}
