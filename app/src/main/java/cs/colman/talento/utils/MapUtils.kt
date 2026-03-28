package cs.colman.talento.utils

import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import cs.colman.talento.BuildConfig
import cs.colman.talento.TAG
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.coroutines.resume

object MapUtils {
    fun initializeMap(context: Context, mapView: MapView, savedInstanceState: Bundle?) {
        MapLibre.getInstance(context, BuildConfig.MAPLIBRE_API_KEY, WellKnownTileServer.MapLibre)
        mapView.onCreate(savedInstanceState)
    }

    fun setupMapStyle(map: MapLibreMap, callback: (() -> Unit)? = null) {
        map.setStyle("https://api.maptiler.com/maps/basic/style.json?key=${BuildConfig.MAPLIBRE_API_KEY}") {
            callback?.invoke()
        }
    }

    @Suppress("DEPRECATION")
    fun addMarker(map: MapLibreMap, position: LatLng, title: String? = null) {
        val markerOptions = MarkerOptions().position(position)
        if (title != null) markerOptions.title(title)
        map.addMarker(markerOptions)
    }

    @Suppress("DEPRECATION")
    fun clearMap(map: MapLibreMap) {
        map.clear()
    }

    fun animateCamera(map: MapLibreMap, position: LatLng, zoom: Double = 15.0) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, zoom))
    }

    fun enableUserLocation(map: MapLibreMap, context: Context): LatLng? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            val locationComponent = map.locationComponent
            locationComponent.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, map.style!!)
                    .build()
            )
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.TRACKING
            locationComponent.renderMode = RenderMode.NORMAL

            val lastLocation = locationComponent.lastKnownLocation

            return if (lastLocation != null) {
                animateCamera(map, LatLng(lastLocation.latitude, lastLocation.longitude))

                LatLng(lastLocation.latitude, lastLocation.longitude)
            } else null
        }
        return null
    }

    @SuppressLint("MissingPermission")
    suspend fun getUserLocationAsync(context: Context): LatLng? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            continuation.resume(LatLng(location.latitude, location.longitude))
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error getting user location: ${e.message}")
                        continuation.resume(null)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user location async: ${e.message}")
            null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupMapTouchHandler(mapView: MapView) {
        mapView.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            v.onTouchEvent(event)
            true
        }
    }

    fun Fragment.bindMapLifecycle(mapView: MapView) {
        val mapLifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                mapView.onStart()
            }

            override fun onResume(owner: LifecycleOwner) {
                mapView.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                mapView.onPause()
            }

            override fun onStop(owner: LifecycleOwner) {
                mapView.onStop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                mapView.onDestroy()
            }
        }

        this.viewLifecycleOwner.lifecycle.addObserver(mapLifecycleObserver)

        this.activity?.application?.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}

            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {
                if (activity == this@bindMapLifecycle.activity) {
                    mapView.onSaveInstanceState(outState)
                }
            }
        })

        @Suppress("OVERRIDE_DEPRECATION")
        val runtimeCallback = object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() {
                mapView.onLowMemory()
            }
            override fun onTrimMemory(level: Int) {}
        }

        this.activity?.application?.registerComponentCallbacks(runtimeCallback)

        this.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                activity?.application?.unregisterComponentCallbacks(runtimeCallback)
                viewLifecycleOwner.lifecycle.removeObserver(this)
            }
        })
    }

    fun getUserLocation(map: MapLibreMap): LatLng? {
        return try {
            val locationComponent = map.locationComponent
            if (locationComponent.isLocationComponentActivated && locationComponent.lastKnownLocation != null) {
                val location = locationComponent.lastKnownLocation
                location?.let { LatLng(it.latitude, location.longitude) }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user location: ${e.message}")
            null
        }
    }

    @Suppress("DEPRECATION")
    fun addMarkerAndReturn(map: MapLibreMap, position: LatLng, title: String? = null): Marker {
        val markerOptions = MarkerOptions().position(position)
        if (title != null) markerOptions.title(title)
        return map.addMarker(markerOptions)
    }

    @Suppress("DEPRECATION")
    fun setupInfoWindowAdapter(
        map: MapLibreMap,
        layoutInflater: LayoutInflater,
        layoutResId: Int,
        bindView: (marker: Marker, view: View) -> Unit
    ) {
        map.setInfoWindowAdapter { marker ->
            val rootView = marker.infoWindow?.view?.parent as? ViewGroup
            val infoView = layoutInflater.inflate(layoutResId, rootView, false)

            bindView(marker, infoView)

            infoView
        }
    }
}
