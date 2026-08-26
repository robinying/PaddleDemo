package com.robinying.paddlevision

/** Loads the project-native bridge. Paddle Lite is verified by the predictor smoke test. */
object NativeBridge {
    init {
        System.loadLibrary("paddle_vision")
    }

    external fun bridgeInfo(): String
}
