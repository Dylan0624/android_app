package com.example.pointcloudviewer

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: PointCloudRenderer
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var legendView: LegendView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val updateHandler = Handler(Looper.getMainLooper())
    private var isUpdating = false
    private val executor = Executors.newSingleThreadExecutor()
    private var lastUpdateTime = 0L
    private var showAxisEnabled = true
    private var showGridEnabled = true
    private var showLegendEnabled = true

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

        val menuButton = android.widget.Button(this).apply {
            text = "☰"
            textSize = 20f
            setPadding(16, 16, 16, 16)
            setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        val topLeftLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
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

        navigationView = NavigationView(this)
        setupNavigationMenu()
        val navParams = DrawerLayout.LayoutParams(
            DrawerLayout.LayoutParams.WRAP_CONTENT,
            DrawerLayout.LayoutParams.MATCH_PARENT
        )
        navParams.gravity = Gravity.START
        navigationView.layoutParams = navParams

        drawerLayout.addView(rootLayout)
        drawerLayout.addView(navigationView)
        setContentView(drawerLayout)

        setupGestureDetectors()

        // 將觸摸事件直接設置給 GLSurfaceView
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

    private fun setupNavigationMenu() {
        val menu = navigationView.menu
        menu.add(android.view.Menu.NONE, 1, android.view.Menu.NONE, "顯示座標軸").setCheckable(true).isChecked = true
        menu.add(android.view.Menu.NONE, 2, android.view.Menu.NONE, "顯示網格").setCheckable(true).isChecked = true
        menu.add(android.view.Menu.NONE, 3, android.view.Menu.NONE, "顯示圖例").setCheckable(true).isChecked = true
        menu.add(android.view.Menu.NONE, 4, android.view.Menu.NONE, "色彩模式: 強度").setCheckable(true).isChecked = true
        menu.add(android.view.Menu.NONE, 5, android.view.Menu.NONE, "色彩模式: 深度").setCheckable(true).isChecked = false
        menu.add(android.view.Menu.NONE, 6, android.view.Menu.NONE, "色彩模式: 顏色").setCheckable(true).isChecked = false
        menu.add(android.view.Menu.NONE, 7, android.view.Menu.NONE, "重置視圖")

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    showAxisEnabled = !showAxisEnabled
                    menuItem.isChecked = showAxisEnabled
                    renderer.setAxisVisibility(showAxisEnabled)
                }
                2 -> {
                    showGridEnabled = !showGridEnabled
                    menuItem.isChecked = showGridEnabled
                    renderer.setGridVisibility(showGridEnabled)
                }
                3 -> {
                    showLegendEnabled = !showLegendEnabled
                    menuItem.isChecked = showLegendEnabled
                    legendView.visibility = if (showLegendEnabled) android.view.View.VISIBLE else android.view.View.GONE
                }
                4 -> {
                    renderer.setColorMode(0)
                    menuItem.isChecked = true
                    menu.findItem(5).isChecked = false
                    menu.findItem(6).isChecked = false
                    legendView.mode = 0
                }
                5 -> {
                    renderer.setColorMode(1)
                    menuItem.isChecked = true
                    menu.findItem(4).isChecked = false
                    menu.findItem(6).isChecked = false
                    legendView.mode = 1
                }
                6 -> {
                    renderer.setColorMode(2)
                    menuItem.isChecked = true
                    menu.findItem(4).isChecked = false
                    menu.findItem(5).isChecked = false
                    legendView.mode = 2
                }
                7 -> {
                    renderer.resetView()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
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
        // 這裡僅作為備用，實際觸摸事件由 GLSurfaceView 處理
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