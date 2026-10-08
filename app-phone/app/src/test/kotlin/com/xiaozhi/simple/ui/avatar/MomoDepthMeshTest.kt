package com.xiaozhi.simple.ui.avatar

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class MomoDepthMeshTest {
    @Test fun meshHasFiniteNormalsValidTextureCoordinatesAndSafeIndices() {
        val vertices = MomoDepthMesh.vertices()
        assertEquals((MomoDepthMesh.GRID+1)*(MomoDepthMesh.GRID+1)*8, vertices.size)
        vertices.asList().chunked(8).forEach { v ->
            assertTrue(v.all { it.isFinite() })
            assertTrue(v[2] in 0f..0.43f)
            assertEquals(1f, sqrt(v[3]*v[3]+v[4]*v[4]+v[5]*v[5]),0.001f)
            assertTrue(v[6] in 0f..1f && v[7] in 0f..1f)
        }
        val indices = MomoDepthMesh.indices()
        assertEquals(MomoDepthMesh.GRID*MomoDepthMesh.GRID*6,indices.size)
        assertTrue(indices.all { (it.toInt() and 65535) < vertices.size/8 })
        assertTrue(vertices.asList().chunked(8).any { it[2] > .4f })
    }
}
