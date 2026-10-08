package com.xiaozhi.simple.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import com.xiaozhi.simple.R
import com.xiaozhi.simple.model.AvatarMood
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

data class DepthFrame(val mood: AvatarMood, val seconds: Float, val speaking: Boolean,
    val animated: Boolean, val reaction: AvatarReaction, val style: AvatarStyle)

/** Real textured depth mesh, not a replacement character or a remote 3D asset. */
class MomoDepthView(context: Context, onFailure: () -> Unit) : GLSurfaceView(context) {
    private val painter = DepthRenderer(context, onFailure)
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8,8,8,8,16,0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause = true
        setRenderer(painter)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
    fun frame(value: DepthFrame) {
        painter.frame = value
        requestRender()
    }
}

private class DepthRenderer(private val context: Context, private val onFailure: () -> Unit) : GLSurfaceView.Renderer {
    @Volatile var frame = DepthFrame(AvatarMood.HAPPY,0f,false,false,
        AvatarReaction.NONE,AvatarStyle())
    private val artwork = MomoRenderer()
    private val bitmap = Bitmap.createBitmap(768,768,Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val vertexData = MomoDepthMesh.vertices()
    private val indexData = MomoDepthMesh.indices()
    private val vertices = ByteBuffer.allocateDirect(vertexData.size*4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertexData); position(0) }
    private val indices = ByteBuffer.allocateDirect(indexData.size*2)
        .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(indexData); position(0) }
    private var program = 0
    private var texture = 0
    private var width = 1
    private var height = 1
    private fun shader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader,source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,compiled,0)
        if (compiled[0] == 0) { GLES20.glDeleteShader(shader); error("Depth shader unavailable") }
        return shader
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            fun raw(id: Int) = context.resources.openRawResource(id).bufferedReader().use { it.readText() }
            val vertex = shader(GLES20.GL_VERTEX_SHADER,raw(R.raw.momo_depth_vertex))
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER,raw(R.raw.momo_depth_fragment))
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program,vertex)
            GLES20.glAttachShader(program,fragment)
            GLES20.glLinkProgram(program)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0)
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
            check(linked[0] != 0)
            val textures = IntArray(1)
            GLES20.glGenTextures(1,textures,0); texture = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glEnable(GLES20.GL_BLEND)
            // Android bitmap textures are premultiplied.
            GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glClearColor(0f,0f,0f,0f)
        } catch (_: Exception) {
            program = 0
            Handler(Looper.getMainLooper()).post(onFailure)
        }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1); this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0,0,width,height)
    }
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return
        val frame = frame
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        artwork.draw(canvas,768f,768f,frame.mood,frame.seconds,frame.speaking,
            frame.animated,frame.reaction,frame.style)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uArt"),0)
        val minimum = minOf(width,height).toFloat()
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uFit"),minimum/width,minimum/height)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uAngle"),
            if(frame.animated) sin(frame.seconds*1.1f)*0.055f else 0f)
        fun attribute(name: String, count: Int, offset: Int) {
            val location = GLES20.glGetAttribLocation(program,name)
            vertices.position(offset)
            GLES20.glVertexAttribPointer(location,count,GLES20.GL_FLOAT,false,8*4,vertices)
            GLES20.glEnableVertexAttribArray(location)
        }
        attribute("aPosition",3,0); attribute("aNormal",3,3); attribute("aUv",2,6)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,indexData.size,GLES20.GL_UNSIGNED_SHORT,indices)
    }
}
