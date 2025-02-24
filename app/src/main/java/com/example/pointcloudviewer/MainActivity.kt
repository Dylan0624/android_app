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
    private lateinit var pointDetailTextView: TextView
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

        pointDetailTextView = TextView(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            text = ""
        }
        val detailLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        detailLayoutParams.gravity = Gravity.TOP or Gravity.END
        detailLayoutParams.topMargin = 16
        detailLayoutParams.rightMargin = 16
        rootLayout.addView(pointDetailTextView, detailLayoutParams)

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
        rootLayout.addView(topLeftLayout)

        legendView = LegendView(this)
        val legendLayoutParams = FrameLayout.LayoutParams(350, 100)
        legendLayoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        legendView.layoutParams = legendLayoutParams
        legendView.mode = renderer.getColorMode()
        rootLayout.addView(legendView)

        val drawerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
        }

        val axisSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val axisLabel = TextView(this).apply {
            text = "顯示座標軸"
            setTextColor(android.graphics.Color.BLACK)
        }
        val axisSwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                renderer.setAxisVisibility(isChecked)
            }
        }
        axisSwitchLayout.addView(axisLabel)
        axisSwitchLayout.addView(axisSwitch)
        drawerContent.addView(axisSwitchLayout)

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
        drawerContent.addView(gridSwitchLayout)

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
        drawerContent.addView(legendSwitchLayout)

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
        drawerContent.addView(colorModeLayout)

        val resetButton = Button(this).apply {
            text = "重置視圖"
            setOnClickListener {
                renderer.resetView()
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            setPadding(0, 8, 0, 8)
        }
        drawerContent.addView(resetButton)

        val pointsRatioLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
            // Block DrawerLayout interception for the surrounding area
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        drawerLayout.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        drawerLayout.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false // Allow child views to handle their events
            }
        }
        val pointsRatioLabel = TextView(this).apply {
            text = "顯示點數比例: 100%"
            setTextColor(android.graphics.Color.BLACK)
        }
        val pointsRatioSeekBar = SeekBar(this).apply {
            max = 100
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val ratio = progress / 100.0f
                    renderer.displayRatio = ratio
                    pointsRatioLabel.text = "顯示點數比例: ${progress}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            // Explicitly block DrawerLayout interception when touching the SeekBar
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        drawerLayout.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        drawerLayout.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false // Allow SeekBar's own events to proceed
            }
        }
        pointsRatioLayout.addView(pointsRatioLabel)
        pointsRatioLayout.addView(pointsRatioSeekBar)
        drawerContent.addView(pointsRatioLayout)

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

            override fun onLongPress(e: MotionEvent) {
                val touchX = e.x
                val touchY = e.y
                glSurfaceView.queueEvent {
                    val pickedPoint = renderer.pickPoint(touchX, touchY, glSurfaceView.width, glSurfaceView.height)
                    runOnUiThread {
                        if (pickedPoint != null) {
                            val detailText = "位置: (%.2f, %.2f, %.2f)\n法向量: (%.2f, %.2f, %.2f)\n強度: %.2f".format(
                                pickedPoint[0], pickedPoint[1], pickedPoint[2],
                                pickedPoint[4], pickedPoint[5], pickedPoint[6],
                                pickedPoint[3]
                            )
                            pointDetailTextView.text = detailText
                        } else {
                            pointDetailTextView.text = "未選取到點"
                        }
                    }
                }
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