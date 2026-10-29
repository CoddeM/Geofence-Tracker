package com.rahul.assignmentrahul.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rahul.assignmentrahul.R
import java.text.DecimalFormat

private val kilometerFormat = DecimalFormat("0.#")

/** "50 m", "250 m", "1.5 km". */
@Composable
fun radiusLabel(meters: Number): String {
    val value = meters.toDouble()
    return if (value >= 1000) {
        stringResource(R.string.radius_kilometers, kilometerFormat.format(value / 1000))
    } else {
        stringResource(R.string.radius_meters, value.toInt())
    }
}
