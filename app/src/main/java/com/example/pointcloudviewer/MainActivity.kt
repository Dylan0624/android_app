package com.example.pointcloudviewer

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: PointCloudRenderer
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var legendView: LegendView
    private lateinit var drawerLayout: DrawerLayout
    private val updateHandler = Handler(Looper.getMainLooper())
    private var isUpdating = false
    private val executor = Executors.newSingleThreadExecutor()
    private var lastUpdateTime = 0L

    companion object {
        private const val TAG = "PointCloudMain"
    }

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity created")

        drawerLayout = DrawerLayout(this)
        val rootLayout = FrameLayout(this)

        glSurfaceView = GLSurfaceView(this)
        glSurfaceView.setEGLContextClientVersion(2)
        renderer = PointCloudRenderer()
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        rootLayout.addView(glSurfaceView)

        val menuButton = Button(this).apply {
            text = "☰"
            textSize = 20f
            setPadding(16, 16, 16, 16)
            setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        val topLeftLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.START
            addView(menuButton)
        }

        legendView = LegendView(this)
        val legendLayoutParams = FrameLayout.LayoutParams(350, 100)
        legendLayoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        legendView.layoutParams = legendLayoutParams
        legendView.mode = renderer.getColorMode()
        rootLayout.addView(legendView)

        rootLayout.addView(topLeftLayout)

        // 自定義側邊欄佈局，添加背景顏色
        val drawerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF")) // 白色背景
        }

        // 座標軸開關
        val axisSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8) // 添加一些垂直間距
        }
        val axisLabel = TextView(this).apply {
            text = "顯示座標軸"
            setTextColor(android.graphics.Color.BLACK) // 黑色文字
        }
        val axisSwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                renderer.setAxisVisibility(isChecked)
            }
        }
        axisSwitchLayout.addView(axisLabel)
        axisSwitchLayout.addView(axisSwitch)

        // 網格開關
        val gridSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val gridLabel = TextView(this).apply {
            text = "顯示網格"
            setTextColor(android.graphics.Color.BLACK)
        }
        val gridSwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                renderer.setGridVisibility(isChecked)
            }
        }
        gridSwitchLayout.addView(gridLabel)
        gridSwitchLayout.addView(gridSwitch)

        // 圖例開關
        val legendSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val legendLabel = TextView(this).apply {
            text = "顯示圖例"
            setTextColor(android.graphics.Color.BLACK)
        }
        val legendSwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                legendView.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        legendSwitchLayout.addView(legendLabel)
        legendSwitchLayout.addView(legendSwitch)

        // 色彩模式下拉列表
        val colorModeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val colorModeLabel = TextView(this).apply {
            text = "色彩模式"
            setTextColor(android.graphics.Color.BLACK)
        }
        val colorModeSpinner = Spinner(this)
        val colorModes = arrayOf("強度", "深度", "顏色")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorModes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        colorModeSpinner.adapter = adapter
        colorModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                renderer.setColorMode(position)
                legendView.mode = position
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        colorModeLayout.addView(colorModeLabel)
        colorModeLayout.addView(colorModeSpinner)

        // 重置視圖按鈕
        val resetButton = Button(this).apply {
            text = "重置視圖"
            setOnClickListener {
                renderer.resetView()
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            setPadding(0, 8, 0, 8)
        }

        // 將所有控制項添加到側邊欄
        drawerContent.addView(axisSwitchLayout)
        drawerContent.addView(gridSwitchLayout)
        drawerContent.addView(legendSwitchLayout)
        drawerContent.addView(colorModeLayout)
        drawerContent.addView(resetButton)

        val drawerParams = DrawerLayout.LayoutParams(
            DrawerLayout.LayoutParams.WRAP_CONTENT,
            DrawerLayout.LayoutParams.MATCH_PARENT
        )
        drawerParams.gravity = Gravity.START
        drawerContent.layoutParams = drawerParams

        drawerLayout.addView(rootLayout)
        drawerLayout.addView(drawerContent)
        setContentView(drawerLayout)

        setupGestureDetectors()

        glSurfaceView.setOnTouchListener { _, event ->
            val scaleHandled = scaleGestureDetector.onTouchEvent(event)
            val gestureHandled = gestureDetector.onTouchEvent(event)
            scaleHandled || gestureHandled
        }

        updateHandler.postDelayed({
            isUpdating = true
            startPointUpdates()
            Log.i(TAG, "Point updates started after 2s delay")
        }, 2000)
    }

    private fun setupGestureDetectors() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (e1 == null || e2 == null) return false
                Log.d(TAG, "onScroll: pointerCount=${e2.pointerCount}, dx=$distanceX, dy=$distanceY")
                if (e2.pointerCount == 1) {
                    renderer.rotate(distanceX, distanceY)
                } else {
                    renderer.translate(-distanceX, distanceY)
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                Log.d(TAG, "onDoubleTap triggered")
                renderer.resetView()
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                Log.d(TAG, "onScale: scaleFactor=${detector.scaleFactor}")
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
        return super.onTouchEvent(event)
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