package com.xiaozhi.simple.ui.avatar

import kotlin.math.sqrt

/** Front-facing 3D relief of the watch artwork: positions + normals + texture coordinates. */
object MomoDepthMesh {
    const val GRID = 80
    private fun height(x: Float, y: Float): Float {
        fun ellipsoid(cx: Float, cy: Float, rx: Float, ry: Float, depth: Float): Float {
            val d = 1f - (x-cx)*(x-cx)/(rx*rx) - (y-cy)*(y-cy)/(ry*ry)
            return if (d > 0f) sqrt(d) * depth else 0f
        }
        return maxOf(ellipsoid(100f,118f,65f,60f,42f),
            ellipsoid(67.5f,56f,17f,46f,14f),
            ellipsoid(132.5f,56f,17f,46f,14f),
            ellipsoid(51f,150f,20f,23f,25f),
            ellipsoid(149f,150f,20f,23f,25f),
            ellipsoid(76f,175f,22f,18f,24f),
            ellipsoid(124f,175f,22f,18f,24f))
    }
    fun vertices(): FloatArray {
        val data = FloatArray((GRID+1)*(GRID+1)*8)
        var at = 0
        for (row in 0..GRID) for (col in 0..GRID) {
            val x = col * 200f/GRID
            val y = row * 200f/GRID
            val dx = (height(x+.5f,y)-height(x-.5f,y))
            val dy = (height(x,y+.5f)-height(x,y-.5f))
            val length = sqrt(dx*dx+dy*dy+1f)
            data[at++] = (x-100f)/100f
            data[at++] = (100f-y)/100f
            data[at++] = height(x,y)/100f
            data[at++] = -dx/length
            data[at++] = dy/length
            data[at++] = 1f/length
            data[at++] = x/200f
            data[at++] = y/200f
        }
        return data
    }
    fun indices(): ShortArray {
        val data = ShortArray(GRID*GRID*6)
        var at = 0
        for (row in 0 until GRID) for (col in 0 until GRID) {
            val a = row*(GRID+1)+col
            for (index in intArrayOf(a,a+GRID+1,a+1,a+1,a+GRID+1,a+GRID+2))
                data[at++] = index.toShort()
        }
        return data
    }
}
