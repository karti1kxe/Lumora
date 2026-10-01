package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val SVG_PATH_DATA =
    "M245.747 616.71 C247.253 605.938 247.758 594.125 249.263 582.917 C252.699 558.842 257.988 535.068 265.085 511.808 C323.005 322.978 484.72 185.828 682.639 167.803 C697.851 166.418 713.245 167.605 728.41 167.099 C774.642 165.556 819.13 174.13 863.094 187.701 C931.463 207.6 996.296 252.943 1057.59 288.698 L1365.73 472.379 L1533.67 572.442 C1568.46 593.231 1601.46 611.029 1634.27 635.397 C1706.43 689 1760.78 759.658 1794.57 843.165 C1837.39 949.791 1842.66 1067.81 1809.53 1177.83 C1785.34 1258.44 1740.81 1331.46 1680.22 1389.86 C1629.52 1438.51 1570.16 1469.61 1510.51 1505.41 C1447.45 1543.72 1384.08 1581.53 1320.41 1618.84 L1086.24 1758.65 C1055.61 1776.83 1022.39 1799.15 991.066 1815.52 C954.513 1838.81 908.183 1864.05 866.806 1875.7 C841.259 1886.61 790.763 1894.91 763.234 1896.99 C747.627 1897.8 731.795 1897.2 716.197 1897.5 C577.968 1900.09 440.33 1827.6 354.169 1721.22 C303.918 1659.18 269.778 1585.31 253.754 1507.15 C243.586 1457.56 244.083 1416.17 244.12 1366.32 L244.126 1266.86 L244.08 942.613 L244.01 733.038 C243.984 703.094 242.414 644.113 245.747 616.71 Z"

/**
 * Exact Vector representation of user SVG icon.
 * Features centered positioning, clean proportions, and radiant 2-stop/3-stop gradient (#52AEEB to #CB60AC).
 */
@Composable
fun PlayGradientLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val basePath = remember {
        PathParser().parsePathString(SVG_PATH_DATA).toPath()
    }
    val bounds = remember { basePath.getBounds() }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cw = this.size.width
            val ch = this.size.height

            // Scaling: Fits nicely inside with slight padding (76% size for optical balance)
            val scale = (cw * 0.76f / bounds.width).coerceAtMost(ch * 0.76f / bounds.height)

            val matrix = Matrix().apply {
                translate(cw / 2f, ch / 2f)
                scale(scale, scale)
                translate(-bounds.center.x, -bounds.center.y)
            }

            val transformedPath = Path().apply {
                addPath(basePath)
                transform(matrix)
            }

            // Gradient matching SVG gradient (#52AEEB to #CB60AC)
            val gradientBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF52AEEB),
                    Color(0xFF818CF8),
                    Color(0xFFCB60AC)
                ),
                start = Offset(cw * 0.15f, ch * 0.15f),
                end = Offset(cw * 0.85f, ch * 0.85f)
            )

            drawPath(path = transformedPath, brush = gradientBrush)
        }
    }
}
