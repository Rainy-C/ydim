package com.yucve.ydim

object DimContract {
    const val APP_PACKAGE = "com.yucve.ydim"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    const val SETTING_KEY = "reduce_bright_colors_activated"

    const val BRIDGE_PERMISSION =
        "com.yucve.ydim.permission.LSPOSED_BRIDGE"

    const val ACTION_LSPOSED_SET =
        "com.yucve.ydim.action.LSPOSED_SET_EXTREME_DIM"
    const val ACTION_LSPOSED_PING =
        "com.yucve.ydim.action.LSPOSED_PING"
    const val ACTION_LSPOSED_REPLY =
        "com.yucve.ydim.action.LSPOSED_REPLY"

    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_SUCCESS = "success"
}
