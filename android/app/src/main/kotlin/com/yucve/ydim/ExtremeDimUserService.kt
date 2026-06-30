package com.yucve.ydim

import java.util.concurrent.TimeUnit

class ExtremeDimUserService : IExtremeDimService.Stub() {
    override fun destroy() {
        System.exit(0)
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        return try {
            val process = ProcessBuilder(
                "settings",
                "put",
                "secure",
                DimContract.SETTING_KEY,
                if (enabled) "1" else "0",
            )
                .redirectErrorStream(true)
                .start()

            process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Throwable) {
            false
        }
    }
}
