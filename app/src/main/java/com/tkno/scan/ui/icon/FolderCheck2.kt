package com.tkno.scan.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FolderCheck2Icon: ImageVector
    get() {
        if (_folderCheck2Icon != null) {
            return _folderCheck2Icon!!
        }
        _folderCheck2Icon = ImageVector.Builder(
            name = "folder_check_2",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.Companion.NonZero,
            ) {
                moveTo(4f, 18f)
                verticalLineTo(6f)
                verticalLineTo(8f)
                verticalLineToRelative(3.3f)
                quadTo(4f, 11.15f, 4f, 11.08f)
                reflectiveQuadTo(4f, 11f)
                reflectiveQuadToRelative(0f, 2.05f)
                reflectiveQuadTo(4f, 18f)
                close()
                moveToRelative(0f, 2f)
                quadTo(3.18f, 20f, 2.59f, 19.41f)
                reflectiveQuadTo(2f, 18f)
                verticalLineTo(6f)
                quadTo(2f, 5.18f, 2.59f, 4.59f)
                reflectiveQuadTo(4f, 4f)
                horizontalLineToRelative(6f)
                lineToRelative(2f, 2f)
                horizontalLineToRelative(8f)
                quadToRelative(0.83f, 0f, 1.41f, 0.59f)
                quadTo(22f, 7.18f, 22f, 8f)
                verticalLineToRelative(4.27f)
                quadTo(21.55f, 11.95f, 21.05f, 11.71f)
                quadTo(20.55f, 11.48f, 20f, 11.3f)
                verticalLineTo(8f)
                horizontalLineTo(11.18f)
                lineToRelative(-2f, -2f)
                horizontalLineTo(4f)
                verticalLineTo(18f)
                horizontalLineToRelative(7f)
                quadToRelative(0f, 0.52f, 0.08f, 1.02f)
                reflectiveQuadTo(11.3f, 20f)
                horizontalLineTo(4f)
                close()
                moveToRelative(13.28f, 0.25f)
                lineTo(20.75f, 16.8f)
                lineTo(19.7f, 15.75f)
                lineToRelative(-2.43f, 2.38f)
                lineTo(16.3f, 17.15f)
                lineToRelative(-1.05f, 1.08f)
                lineToRelative(2.03f, 2.02f)
                close()
                moveToRelative(4.26f, -5.79f)
                quadTo(23f, 15.93f, 23f, 18f)
                reflectiveQuadToRelative(-1.46f, 3.54f)
                reflectiveQuadTo(18f, 23f)
                quadToRelative(-2.07f, 0f, -3.54f, -1.46f)
                reflectiveQuadTo(13f, 18f)
                reflectiveQuadToRelative(1.46f, -3.54f)
                reflectiveQuadTo(18f, 13f)
                reflectiveQuadToRelative(3.54f, 1.46f)
                close()
            }
        }.build()
        return _folderCheck2Icon!!
    }

private var _folderCheck2Icon: ImageVector? = null
