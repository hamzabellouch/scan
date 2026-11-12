package com.tkno.scan.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tkno.scan.R

@Composable
fun ConfirmButton(
    modifier: Modifier = Modifier,
    text: String = "Confirm",
    icon: ImageVector? = Icons.Outlined.Check,
    enabled: Boolean = true,
    colors: ButtonColors =
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        contentPadding =
            if (icon != null) ButtonDefaults.ButtonWithIconContentPadding
            else ButtonDefaults.ContentPadding,
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                imageVector = icon,
                contentDescription = null,
            )
            Text(modifier = Modifier.padding(start = 8.dp), text = text)
        } else {
            Text(text = text)
        }
    }
}

@Composable
fun DismissButton(
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.cancel),
    icon: ImageVector? = Icons.Outlined.Cancel,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        contentPadding =
            if (icon != null) ButtonDefaults.ButtonWithIconContentPadding
            else ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                imageVector = icon,
                contentDescription = null,
            )
            Text(modifier = Modifier.padding(start = 8.dp), text = text, color = textColor)
        } else {
            Text(text = text, color = textColor)
        }
    }
}

@Composable
fun OutlinedButtonWithIcon(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = Color.Unspecified,
) {
    DismissButton(
        modifier = modifier,
        text = text,
        icon = icon,
        contentColor = contentColor,
        textColor = textColor,
        onClick = onClick,
    )
}

@Composable
fun FilledButtonWithIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ConfirmButton(
        modifier = modifier,
        text = text,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
    )
}


