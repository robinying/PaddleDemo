package com.robinying.paddlevision.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import com.robinying.paddlevision.UiText

/**
 * Resolves a [UiText] for display. Arguments that are themselves [UiText] are resolved
 * first, so a message can embed another localized fragment. A [UiText.quantity] turns the
 * lookup into a `<plurals>` lookup.
 */
@Composable
internal fun localizedText(text: UiText): String {
    val args = text.args.map { argument ->
        if (argument is UiText) localizedText(argument) else argument
    }.toTypedArray()
    val quantity = text.quantity
    return if (quantity != null) {
        pluralStringResource(text.resourceId, quantity, *args)
    } else {
        stringResource(text.resourceId, *args)
    }
}
