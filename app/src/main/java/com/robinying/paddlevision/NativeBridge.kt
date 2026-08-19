package com.robinying.paddlevision

import java.io.File

/**
 * Loads the small project-native bridge. Paddle Lite inference itself uses the
 * matching v2.10-rc Java/JNI runtime bundled in `PaddlePredictor.jar`.
 */
object NativeBridge {
    init {
        System.loadLibrary("paddle_vision")
    }

    external fun runtimeInfo(): String

    external fun isRuntimeAvailable(): Boolean
}
