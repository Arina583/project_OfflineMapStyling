package com.example.offlinemapstyling

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.ComponentActivity
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MBTilesFileArchive
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

class MainActivity : ComponentActivity()  {
    companion object {
        const val MIN_ZOOM = 13; const val MAX_ZOOM = 16
    }

    lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.map)

        // копируем архив из assets в filesDir (однократно)
        val tilesFile = copyAsset("light.mbtiles")

        // --- офлайн-провайдер: цепочка только из архивного провайдера ---
        val source = XYTileSource("offline", MIN_ZOOM, MAX_ZOOM, 256, ".png", arrayOf())
        val registerReceiver = SimpleRegisterReceiver(applicationContext)
        val archive = MBTilesFileArchive.getDatabaseFileArchive(tilesFile)
        val archiveProvider = MapTileFileArchiveProvider(
            registerReceiver, source, arrayOf<IArchiveFile>(archive)
        )
        mapView.tileProvider = MapTileProviderArray(
            source, registerReceiver, arrayOf<MapTileModuleProviderBase>(archiveProvider)
        )
        mapView.setUseDataConnection(false)

        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(org.osmdroid.util.GeoPoint(56.1290, 40.4070))

        // подключение кнопки переключения стиля
        findViewById<Button>(R.id.btnStyle).setOnClickListener { toggleStyle() }
        // слой POI с кластеризацией
        addPois()
    }

    private fun copyAsset(name: String): File {
        val f = File(filesDir, name)
        if (!f.exists()) assets.open(name).copyTo(f.outputStream())
        return f
    }

    private fun addPois() {
        val clusterer = RadiusMarkerClusterer(this)
        clusterer.setRadius(60)                  // радиус кластера, экранных пикселей
        clusterer.setMaxClusteringZoomLevel(15)  // выше зума 15 — только маркеры

        val rng = java.util.Random(42)           // детерминированный набор точек
        repeat(100) { i ->
            val lat = 56.08 + rng.nextDouble() * 0.10
            val lon = 40.30 + rng.nextDouble() * 0.20
            val m = Marker(mapView)
            m.position = org.osmdroid.util.GeoPoint(lat, lon)
            m.title = "POI $i"
            clusterer.add(m)
        }
        mapView.overlays.add(clusterer)

        // лог числа кластеров при смене зума
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?) = false
            override fun onZoom(event: ZoomEvent?): Boolean {
                val z = event!!.zoomLevel
                Log.i("Cluster", "zoom=$z кластеров=${clusterer.clusterer(mapView).size}")
                return false
            }
        })
    }

    private var dark = false

    fun toggleStyle() {
        dark = !dark
        val file = copyAsset(if (dark) "dark.mbtiles" else "light.mbtiles")
        val source = XYTileSource("offline", MIN_ZOOM, MAX_ZOOM, 256, ".png", arrayOf())
        val registerReceiver = SimpleRegisterReceiver(applicationContext)
        val archive = MBTilesFileArchive.getDatabaseFileArchive(file)
        val archiveProvider = MapTileFileArchiveProvider(
            registerReceiver, source, arrayOf<IArchiveFile>(archive))
        mapView.tileProvider = MapTileProviderArray(
            source, registerReceiver, arrayOf<MapTileModuleProviderBase>(archiveProvider))
        mapView.setUseDataConnection(false)
        // Overlays (кластер) независимы от тайлов — их пересоздавать не нужно
    }
}