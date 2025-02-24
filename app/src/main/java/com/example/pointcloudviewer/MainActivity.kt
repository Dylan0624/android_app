package com.example.pointcloudviewer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: PointCloudRenderer
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var axisToggleButton: Button
    private lateinit var resetButton: Button
    private lateinit var colorModeButton: Button
    private lateinit var legendView: LegendView
    private val updateHandler = Handler(Looper.getMainLooper())
    private var isUpdating = false
    private val executor = Executors.newSingleThreadExecutor()
    private var lastUpdateTime = 0L

    companion object {
        private const val TAG = "PointCloudMain"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created")

        val rootLayout = FrameLayout(this)

        glSurfaceView = GLSurfaceView(this)
        glSurfaceView.setEGLContextClientVersion(2)
        renderer = PointCloudRenderer()
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        rootLayout.addView(glSurfaceView)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.END
            setPadding(16, 16, 16, 16)
        }

        axisToggleButton = Button(this).apply {
            text = "座標軸: 開"
            setOnClickListener {
                renderer.toggleAxis()
                text = if (renderer.isAxisVisible()) "座標軸: 開" else "座標軸: 關"
            }
        }

        resetButton = Button(this).apply {
            text = "重置視圖"
            setOnClickListener { renderer.resetView() }
        }

        colorModeButton = Button(this).apply {
            text = "色彩模式: 強度"
            setOnClickListener {
                renderer.cycleColorMode()
                when (renderer.getColorMode()) {
                    0 -> {
                        text = "色彩模式: 強度"
                        legendView.mode = 0
                    }
                    1 -> {
                        text = "色彩模式: 深度"
                        legendView.mode = 1
                    }
                    2 -> {
                        text = "色彩模式: 顏色"
                        legendView.mode = 2
                    }
                }
            }
        }

        buttonLayout.addView(axisToggleButton)
        buttonLayout.addView(resetButton)
        buttonLayout.addView(colorModeButton)
        rootLayout.addView(buttonLayout)

        legendView = LegendView(this)
        val legendLayoutParams = FrameLayout.LayoutParams(350, 100)
        legendLayoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        legendView.layoutParams = legendLayoutParams
        legendView.mode = renderer.getColorMode()
        rootLayout.addView(legendView)

        setContentView(rootLayout)

        setupGestureDetectors()
        updateHandler.postDelayed({
            isUpdating = true
            startPointUpdates()
            Log.i(TAG, "Point updates started after 2s delay")
        }, 2000)
    }

    private fun setupGestureDetectors() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e2.pointerCount == 1) {
                    renderer.rotate(distanceX, distanceY)  // 單指旋轉
                } else {
                    renderer.translate(-distanceX, distanceY)  // 多指平移

                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                renderer.resetView()
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.scale(detector.scaleFactor)
                return true
            }
        })
    }

    private fun startPointUpdates() {
        updateHandler.post(object : Runnable {
            override fun run() {
                if (isUpdating) {
                    val currentTime = System.currentTimeMillis()
                    if (lastUpdateTime > 0) {
                        val deltaTime = currentTime - lastUpdateTime
                        val fps = 1000f / deltaTime
                        Log.d(TAG, "FPS: %.1f".format(fps))
                    }
                    lastUpdateTime = currentTime

                    executor.execute {
                        val startTime = System.currentTimeMillis()
                        val points = renderer.generateSimulatedPointsBatch()
                        val genTime = System.currentTimeMillis() - startTime
                        Log.d(TAG, "Point generation took: ${genTime}ms")
                        glSurfaceView.queueEvent { renderer.updatePoints(points) }
                    }
                    updateHandler.postDelayed(this, 33)
                }
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        isUpdating = false
        updateHandler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Activity paused")
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        if (!isUpdating) {
            updateHandler.postDelayed({
                isUpdating = true
                startPointUpdates()
                Log.i(TAG, "Point updates resumed after 2s delay")
            }, 2000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isUpdating = false
        updateHandler.removeCallbacksAndMessages(null)
        executor.shutdown()
        Log.i(TAG, "Activity destroyed, executor shutdown")
    }
}