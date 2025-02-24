package com.example.pointcloudviewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PointCloudRenderer : GLSurfaceView.Renderer {
    private val maxPoints = 850000
    private val pointsPerUpdate = 850000
    private val floatsPerPoint = 7

    private val pointData = FloatArray(maxPoints * floatsPerPoint)
    private var numPoints = 0
    private var startPoint = 0
    private var vertexBuffer: FloatBuffer? = null
    private var program = 0
    private var isInitialized = false
    private var isReadyToRender = false

    private var showAxis = true
    private lateinit var axisPosBuffer: FloatBuffer
    private lateinit var axisColorBuffer: FloatBuffer
    private var axisProgram = 0

    private val BYTES_PER_FLOAT = 4
    private val pointVertexSize = 4

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var scaleFactor = 1.0f
    private var rotateX = 0.0f
    private var rotateY = 0.0f

    companion object {
        private const val TAG = "PointCloudRenderer"
    }

    private val axisVertices = floatArrayOf(
        0f, 0f, 0f, 1f, 0f, 0f, 1f,
        1f, 0f, 0f, 1f, 0f, 0f, 1f,
        0f, 0f, 0f, 0f, 1f, 0f, 1f,
        0f, 1f, 0f, 0f, 1f, 0f, 1f,
        0f, 0f, 0f, 0f, 0f, 1f, 1f,
        0f, 0f, 1f, 0f, 0f, 1f, 1f
    )

    private val vertexShaderCodeStr = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        varying float vIntensity;
        void main() {
            gl_Position = uMVPMatrix * vec4(aPosition.xyz, 1.0);
            gl_PointSize = 2.0;
            vIntensity = aPosition.w;
        }
    """.trimIndent()

    private val fragmentShaderCodeStr = """
        precision mediump float;
        varying float vIntensity;
        void main() {
            float normalizedIntensity = vIntensity / 255.0;
            gl_FragColor = vec4(normalizedIntensity, normalizedIntensity, normalizedIntensity, 1.0);
        }
    """.trimIndent()

    private val axisVertexShaderCodeStr = """
        uniform mat4 uMVPMatrix;
        attribute vec3 aPosition;
        attribute vec4 aColor;
        varying vec4 vColor;
        void main() {
            gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            vColor = aColor;
        }
    """.trimIndent()

    private val axisFragmentShaderCodeStr = """
        precision mediump float;
        varying vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """.trimIndent()

    fun generateSimulatedPointsBatch(): FloatArray {
        val batch = FloatArray(pointsPerUpdate * floatsPerPoint)
        val rings = 32
        val pointsPerRing = pointsPerUpdate / rings
        var offset = 0
        for (ring in 0 until rings) {
            val verticalAngle = -15f + (ring * 1f)
            for (i in 0 until pointsPerRing) {
                val horizontalAngle = (i.toFloat() / pointsPerRing) * 360f
                val distance = 0.5f + Random.nextFloat() * 0.5f
                val rad_h = Math.toRadians(horizontalAngle.toDouble())
                val rad_v = Math.toRadians(verticalAngle.toDouble())
                val x = (distance * cos(rad_v) * cos(rad_h)).toFloat()
                val y = (distance * cos(rad_v) * sin(rad_h)).toFloat()
                val z = (distance * sin(rad_v)).toFloat()
                val intensity = Random.nextFloat() * 255f
                val norm = if (distance != 0f) distance else 1f
                val nx = x / norm
                val ny = y / norm
                val nz = z / norm
                batch[offset++] = x
                batch[offset++] = y
                batch[offset++] = z
                batch[offset++] = intensity
                batch[offset++] = nx
                batch[offset++] = ny
                batch[offset++] = nz
            }
        }
        return batch
    }

    fun updatePoints(newBatch: FloatArray) {
        if (!isInitialized) return
        val startTime = System.currentTimeMillis()
        if (numPoints < maxPoints) {
            val available = maxPoints - numPoints
            if (pointsPerUpdate <= available) {
                val startPos = numPoints * floatsPerPoint
                System.arraycopy(newBatch, 0, pointData, startPos, newBatch.size)
                numPoints += pointsPerUpdate
            } else {
                val toAdd = available
                val startPos = numPoints * floatsPerPoint
                System.arraycopy(newBatch, 0, pointData, startPos, toAdd * floatsPerPoint)
                numPoints = maxPoints
                val remaining = pointsPerUpdate - toAdd
                for (i in 0 until remaining) {
                    val idx = (startPoint + i) % maxPoints
                    System.arraycopy(newBatch, (toAdd + i) * floatsPerPoint, pointData, idx * floatsPerPoint, floatsPerPoint)
                }
                startPoint = (startPoint + remaining) % maxPoints
            }
        } else {
            for (i in 0 until pointsPerUpdate) {
                val idx = (startPoint + i) % maxPoints
                System.arraycopy(newBatch, i * floatsPerPoint, pointData, idx * floatsPerPoint, floatsPerPoint)
            }
            startPoint = (startPoint + pointsPerUpdate) % maxPoints
        }
        updateVertexBuffer()
        isReadyToRender = true
        val updateTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Point update took: ${updateTime}ms, numPoints: $numPoints")
    }

    private fun updateVertexBuffer() {
        vertexBuffer?.let { buffer ->
            buffer.clear()
            for (i in 0 until numPoints) {
                val idx = (startPoint + i) % maxPoints
                val base = idx * floatsPerPoint
                buffer.put(pointData[base])
                buffer.put(pointData[base + 1])
                buffer.put(pointData[base + 2])
                buffer.put(pointData[base + 3])
            }
            buffer.position(0)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        initShaders()
        initAxisShaders()
        setupPointCloud()
        setupAxisBuffers()
        Matrix.setIdentityM(modelMatrix, 0)
        isInitialized = true
        Log.i(TAG, "Surface created, OpenGL initialized")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 10f)
        Log.i(TAG, "Surface changed: width=$width, height=$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!isInitialized) return
        val startTime = System.currentTimeMillis()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotateX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotateY, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, scaleFactor, scaleFactor, scaleFactor)
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        if (isReadyToRender) {
            drawPointCloud()
        }
        if (showAxis) drawAxis()

        val frameTime = System.currentTimeMillis() - startTime
        if (frameTime > 16) { // 若超過 16ms (60 FPS) 則警告
            Log.w(TAG, "Frame render took: ${frameTime}ms")
        }
    }

    private fun drawPointCloud() {
        GLES20.glUseProgram(program)
        val matrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvpMatrix, 0)
        vertexBuffer?.let { buffer ->
            val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(
                positionHandle,
                pointVertexSize,
                GLES20.GL_FLOAT,
                false,
                pointVertexSize * BYTES_PER_FLOAT,
                buffer
            )
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints)
            GLES20.glDisableVertexAttribArray(positionHandle)
        }
    }

    private fun drawAxis() {
        GLES20.glUseProgram(axisProgram)
        val matrixHandle = GLES20.glGetUniformLocation(axisProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvpMatrix, 0)
        val posHandle = GLES20.glGetAttribLocation(axisProgram, "aPosition")
        val colorHandle = GLES20.glGetAttribLocation(axisProgram, "aColor")
        axisPosBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, axisPosBuffer)
        axisColorBuffer.position(0)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, axisColorBuffer)
        GLES20.glLineWidth(1.0f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 6)
        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun initShaders() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCodeStr)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCodeStr)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
    }

    private fun initAxisShaders() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, axisVertexShaderCodeStr)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, axisFragmentShaderCodeStr)
        axisProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(axisProgram, vertexShader)
        GLES20.glAttachShader(axisProgram, fragmentShader)
        GLES20.glLinkProgram(axisProgram)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Error compiling shader: $error")
        }
        return shader
    }

    private fun setupPointCloud() {
        val vb = ByteBuffer.allocateDirect(maxPoints * pointVertexSize * BYTES_PER_FLOAT)
        vb.order(ByteOrder.nativeOrder())
        vertexBuffer = vb.asFloatBuffer()
    }

    private fun setupAxisBuffers() {
        val vertexCount = 6
        axisPosBuffer = ByteBuffer.allocateDirect(vertexCount * 3 * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        axisColorBuffer = ByteBuffer.allocateDirect(vertexCount * 4 * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until vertexCount) {
            val offset = i * 7
            axisPosBuffer.put(axisVertices, offset, 3)
            axisColorBuffer.put(axisVertices, offset + 3, 4)
        }
        axisPosBuffer.position(0)
        axisColorBuffer.position(0)
    }

    fun rotate(dx: Float, dy: Float) {
        rotateX += dy / 5f
        rotateY += dx / 5f
    }

    fun scale(factor: Float) {
        scaleFactor *= factor
        scaleFactor = scaleFactor.coerceIn(0.1f, 5.0f)
    }

    fun resetView() {
        scaleFactor = 1.0f
        rotateX = 0f
        rotateY = 0f
    }

    fun toggleAxis() {
        showAxis = !showAxis
    }

    fun isAxisVisible(): Boolean = showAxis
}