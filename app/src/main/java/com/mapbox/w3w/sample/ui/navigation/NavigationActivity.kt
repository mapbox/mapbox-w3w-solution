package com.mapbox.w3w.sample.ui.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.directions.v5.models.VoiceInstructions
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.internal.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraStateChangedObserver
import com.mapbox.navigation.ui.maps.camera.view.MapboxRecenterButton
import com.mapbox.navigation.ui.maps.camera.view.MapboxRouteOverviewButton
import com.mapbox.navigation.ui.maps.internal.route.line.MapboxRouteLineApiExtensions.setRoutes
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLine
import com.mapbox.navigation.ui.tripprogress.api.MapboxTripProgressApi
import com.mapbox.navigation.ui.tripprogress.model.DistanceRemainingFormatter
import com.mapbox.navigation.ui.tripprogress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.ui.tripprogress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.ui.tripprogress.model.TimeRemainingFormatter
import com.mapbox.navigation.ui.tripprogress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.voice.api.MapboxSpeechApi
import com.mapbox.navigation.ui.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.ui.voice.model.SpeechAnnouncement
import com.mapbox.navigation.ui.voice.model.SpeechError
import com.mapbox.navigation.ui.voice.model.SpeechValue
import com.mapbox.navigation.ui.voice.model.SpeechVolume
import com.mapbox.navigation.ui.voice.view.MapboxSoundButton
import com.mapbox.w3w.sample.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

const val EXTRA_NAVIGATION_POINT = "com.mapbox.w3w.sample.ui.map.NavigationActivity.extra.point"

class NavigationActivity : AppCompatActivity() {

    /* ----- Mapbox Maps components ----- */
    private lateinit var mapboxMap: MapboxMap

    /* ----- Mapbox Navigation components ----- */
    private lateinit var mapboxNavigation: MapboxNavigation

    // location puck integration
    private val navigationLocationProvider = NavigationLocationProvider()

    // camera
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var permissionsManager: PermissionsManager
    private val pixelDensity = Resources.getSystem().displayMetrics.density
    private val overviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            140.0 * pixelDensity,
            40.0 * pixelDensity,
            120.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }
    private val landscapeOverviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            20.0 * pixelDensity,
            20.0 * pixelDensity
        )
    }
    private val followingPadding: EdgeInsets by lazy {
        EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }
    private val landscapeFollowingPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    // trip progress bottom view
    private lateinit var tripProgressApi: MapboxTripProgressApi

    // voice instructions
    private var isVoiceInstructionsMuted = false
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var speechAPI: MapboxSpeechApi
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer

    // route line
    private lateinit var routeLineAPI: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private lateinit var routeArrowView: MapboxRouteArrowView
    private val routeArrowAPI: MapboxRouteArrowApi = MapboxRouteArrowApi()

    /* ----- Voice instruction callbacks ----- */
    private val voiceInstructionsObserver = object : VoiceInstructionsObserver {
        override fun onNewVoiceInstructions(voiceInstructions: VoiceInstructions) {
            speechAPI.generate(
                voiceInstructions,
                speechCallback
            )
        }
    }

    private val voiceInstructionsPlayerCallback =
        object : MapboxNavigationConsumer<SpeechAnnouncement> {
            override fun accept(value: SpeechAnnouncement) {
                // remove already consumed file to free-up space
                speechAPI.clean(value)
            }
        }

    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error ->
                    // play the instruction via fallback text-to-speech engine
                    voiceInstructionsPlayer.play(
                        error.fallback,
                        voiceInstructionsPlayerCallback
                    )
                },
                { value ->
                    // play the sound file from the external generator
                    voiceInstructionsPlayer.play(
                        value.announcement,
                        voiceInstructionsPlayerCallback
                    )
                }
            )
        }

    /* ----- Location and route progress callbacks ----- */
    private val locationObserver = object : LocationObserver {
        override fun onRawLocationChanged(rawLocation: Location) {
            // not handled
        }

        override fun onEnhancedLocationChanged(
            enhancedLocation: Location,
            keyPoints: List<Location>
        ) {
            // Some systems (especially Android emulator) will sometimes provide invalid locations
            if ((enhancedLocation.latitude == 0.0 && enhancedLocation.longitude == 0.0)
                || (enhancedLocation.latitude > 90.0)
                || (enhancedLocation.latitude < -90.0)
                || (enhancedLocation.longitude > 180.0)
                || (enhancedLocation.longitude < -180.0)
            ) {
                Log.e("NavigationActivity", "lat " + enhancedLocation.latitude + ", long " + enhancedLocation.longitude)
                return
            }
            // update location puck's position on the map
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = keyPoints
            )

            // update camera position to account for new location
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()
        }
    }

    private val routeProgressObserver = object : RouteProgressObserver {
        override fun onRouteProgressChanged(routeProgress: RouteProgress) {
            // update the camera position to account for the progressed fragment of the route
            viewportDataSource.onRouteProgressChanged(routeProgress)
            viewportDataSource.evaluate()

            // show arrow on the route line with the next maneuver
            val maneuverArrowResult = routeArrowAPI.addUpcomingManeuverArrow(routeProgress)
            val style = mapboxMap.getStyle()
            if (style != null) {
                routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
            }

            // update top maneuver instructions
            maneuverApi.getUpcomingManeuverList(
                routeProgress
            ) { maneuver ->
                maneuver.onValue {
                    findViewById<MapboxManeuverView>(R.id.maneuverView).renderUpcomingManeuvers(it)
                }
            }

            val routeStepProgress = routeProgress.currentLegProgress?.currentStepProgress
            if (routeStepProgress != null) {
                maneuverApi.getStepDistanceRemaining(
                    routeStepProgress
                ) { distanceRemaining ->
                    distanceRemaining.onValue {
                        findViewById<MapboxManeuverView>(R.id.maneuverView).renderDistanceRemaining(it)
                    }
                }
            }

            // update bottom trip progress summary
            findViewById<MapboxTripProgressView>(R.id.tripProgressView).render(
                tripProgressApi.getTripProgress(
                    routeProgress
                )
            )
        }
    }

    /* ----- Maneuver instruction callbacks ----- */
    private val bannerInstructionsObserver = BannerInstructionsObserver { bannerInstructions ->
        findViewById<MapboxManeuverView>(R.id.maneuverView).visibility = VISIBLE
        maneuverApi.getManeuver(
            bannerInstructions
        ) { maneuver ->
            // updates the maneuver view whenever new data is available
            findViewById<MapboxManeuverView>(R.id.maneuverView).renderManeuver(maneuver)
        }
    }

    private val routesObserver = RoutesObserver { routes ->
        if (routes.isNotEmpty()) {
            // generate route geometries asynchronously and render them
            CoroutineScope(Dispatchers.Main).launch {
                val result = routeLineAPI.setRoutes(
                    listOf(RouteLine(routes.first(), null))
                )
                val style = mapboxMap.getStyle()
                if (style != null) {
                    routeLineView.renderRouteDrawData(style, result)
                }
            }
            // update the camera position to account for the new route
            viewportDataSource.onRouteChanged(routes.first())
            viewportDataSource.evaluate()
        } else {
            // remove the route line and route arrow from the map
            val style = mapboxMap.getStyle()
            if (style != null) {
                routeLineAPI.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(
                        style,
                        value
                    )
                }
                routeArrowView.render(style, routeArrowAPI.clearArrows())
            }

            // remove the route reference to change camera position
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nav)
        mapboxMap = findViewById<MapView>(R.id.mapView).getMapboxMap()
        checkPermissions {
            // initialize the location puck
            findViewById<MapView>(R.id.mapView).location.apply {
                this.locationPuck = LocationPuck2D(
                    bearingImage = ContextCompat.getDrawable(
                        this@NavigationActivity,
                        R.drawable.mapbox_navigation_puck_icon
                    )
                )
                setLocationProvider(navigationLocationProvider)
                enabled = true
            }

            // initialize Mapbox Navigation
            mapboxNavigation = MapboxNavigation(
                NavigationOptions.Builder(this)
                    .accessToken(getString(R.string.mapbox_access_token))
                    .build()
            )

            // Navigate to provided destination
            val destination = intent.getSerializableExtra(EXTRA_NAVIGATION_POINT) as? Point
            destination?.let {
                    findRoute(destination)
            }

            val locationService = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationService.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                .also {
                    Log.d("NavigationActivity", "init location: $it")
                }?.let { currentLocation ->
                val cameraOptions = CameraOptions.Builder()
                    .center(Point.fromLngLat(currentLocation.longitude, currentLocation.latitude))
                    .zoom(13.0)
                    .build()
                mapboxMap.setCamera(cameraOptions)
            }

            // initialize Navigation Camera
            viewportDataSource = MapboxNavigationViewportDataSource(
                findViewById<MapView>(R.id.mapView).getMapboxMap()
            )
            navigationCamera = NavigationCamera(
                findViewById<MapView>(R.id.mapView).getMapboxMap(),
                findViewById<MapView>(R.id.mapView).camera,
                viewportDataSource
            )
            findViewById<MapView>(R.id.mapView).camera.addCameraAnimationsLifecycleListener(
                NavigationBasicGesturesHandler(navigationCamera)
            )
            navigationCamera.registerNavigationCameraStateChangeObserver(
                object : NavigationCameraStateChangedObserver {
                    override fun onNavigationCameraStateChanged(
                        navigationCameraState: NavigationCameraState
                    ) {
                        // shows/hide the recenter button depending on the camera state
                        when (navigationCameraState) {
                            NavigationCameraState.TRANSITION_TO_FOLLOWING,
                            NavigationCameraState.FOLLOWING -> findViewById<MapboxRecenterButton>(R.id.recenter).visibility =
                                INVISIBLE

                            NavigationCameraState.TRANSITION_TO_OVERVIEW,
                            NavigationCameraState.OVERVIEW,
                            NavigationCameraState.IDLE -> findViewById<MapboxRecenterButton>(R.id.recenter).visibility =
                                VISIBLE
                        }
                    }
                }
            )
            if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                viewportDataSource.overviewPadding = landscapeOverviewPadding
            } else {
                viewportDataSource.overviewPadding = overviewPadding
            }
            if (this.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                viewportDataSource.followingPadding = landscapeFollowingPadding
            } else {
                viewportDataSource.followingPadding = followingPadding
            }

            // initialize top maneuver view
            maneuverApi = MapboxManeuverApi(
                MapboxDistanceFormatter(DistanceFormatterOptions.Builder(this).build())
            )

            // initialize bottom progress view
            tripProgressApi = MapboxTripProgressApi(
                TripProgressUpdateFormatter.Builder(this)
                    .distanceRemainingFormatter(
                        DistanceRemainingFormatter(
                            mapboxNavigation.navigationOptions.distanceFormatterOptions
                        )
                    )
                    .timeRemainingFormatter(TimeRemainingFormatter(this))
                    .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
                    .estimatedTimeToArrivalFormatter(
                        EstimatedTimeToArrivalFormatter(this, TimeFormat.NONE_SPECIFIED)
                    )
                    .build()
            )

            // initialize voice instructions
            speechAPI = MapboxSpeechApi(
                this,
                getString(R.string.mapbox_access_token),
                Locale.US.language
            )
            voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
                this,
                getString(R.string.mapbox_access_token),
                Locale.US.language
            )

            // initialize route line
            val mapboxRouteLineOptions = MapboxRouteLineOptions.Builder(this)
                .withRouteLineBelowLayerId("road-label")
                .build()
            routeLineAPI = MapboxRouteLineApi(mapboxRouteLineOptions)
            routeLineView = MapboxRouteLineView(mapboxRouteLineOptions)
            val routeArrowOptions = RouteArrowOptions.Builder(this).build()
            routeArrowView = MapboxRouteArrowView(routeArrowOptions)

            // load map style
            mapboxMap.loadStyleUri(
                Style.MAPBOX_STREETS,
                object : Style.OnStyleLoaded {
                    override fun onStyleLoaded(style: Style) {
                        // add long click listener that search for a route to the clicked destination
                        findViewById<MapView>(R.id.mapView).gestures.addOnMapLongClickListener(
                            object : OnMapLongClickListener {
                                override fun onMapLongClick(point: Point): Boolean {
                                    findRoute(point)
                                    return true
                                }
                            }
                        )
                    }
                }
            )

            // initialize view interactions
            findViewById<ImageView>(R.id.stop).setOnClickListener {
                clearRouteAndStopNavigation()
            }
            findViewById<MapboxRecenterButton>(R.id.recenter).setOnClickListener {
                navigationCamera.requestNavigationCameraToFollowing()
            }
            findViewById<MapboxRouteOverviewButton>(R.id.routeOverview).setOnClickListener {
                navigationCamera.requestNavigationCameraToOverview()
                findViewById<MapboxRecenterButton>(R.id.recenter).showTextAndExtend(2000L)
            }
            findViewById<MapboxSoundButton>(R.id.soundButton).setOnClickListener {
                // mute/unmute voice instructions
                isVoiceInstructionsMuted = !isVoiceInstructionsMuted
                if (isVoiceInstructionsMuted) {
                    findViewById<MapboxSoundButton>(R.id.soundButton).muteAndExtend(2000L)
                    voiceInstructionsPlayer.volume(SpeechVolume(0f))
                } else {
                    findViewById<MapboxSoundButton>(R.id.soundButton).unmuteAndExtend(2000L)
                    voiceInstructionsPlayer.volume(SpeechVolume(1f))
                }
            }

            // start the trip session to being receiving location updates in free drive
            // and later when a route is set, also receiving route progress updates
            mapboxNavigation.startTripSession()
        }
    }

    private fun checkPermissions(onMapReady: () -> Unit) {
        if (PermissionsManager.areLocationPermissionsGranted(this@NavigationActivity)) {
            onMapReady()
        } else {
            permissionsManager = PermissionsManager(object : PermissionsListener {
                override fun onExplanationNeeded(permissionsToExplain: List<String>) {
                    Toast.makeText(
                        this@NavigationActivity, "You need to accept location permissions.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onPermissionResult(granted: Boolean) {
                    if (granted) {
                        onMapReady()
                    } else {
                        this@NavigationActivity.finish()
                    }
                }
            })
            permissionsManager.requestLocationPermissions(this@NavigationActivity)
        }
    }

    override fun onStart() {
        super.onStart()
        findViewById<MapView>(R.id.mapView).onStart()
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.registerBannerInstructionsObserver(bannerInstructionsObserver)
    }

    override fun onStop() {
        super.onStop()
        findViewById<MapView>(R.id.mapView).onStop()
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.unregisterBannerInstructionsObserver(bannerInstructionsObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        findViewById<MapView>(R.id.mapView).onDestroy()
        mapboxNavigation.onDestroy()
        speechAPI.cancel()
        voiceInstructionsPlayer.shutdown()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        findViewById<MapView>(R.id.mapView).onLowMemory()
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val locationService = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        val providers: List<String> = locationService.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l: Location = locationService.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                // Found best last known location: %s", l);
                bestLocation = l
            }
        }
        return bestLocation
    }

    @SuppressLint("MissingPermission")
    private fun findRoute(destination: Point) {
        val origin = getLastKnownLocation().also {
            Log.d("NavigationActivity", "origin location: $it")
        } ?: return

        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .accessToken(getString(R.string.mapbox_access_token))
                .coordinates(
                    listOf(
                        Point.fromLngLat(origin.longitude, origin.latitude),
                        destination
                    )
                )
                .build(),
            object : RoutesRequestCallback {
                override fun onRoutesReady(routes: List<DirectionsRoute>) {
                    setRouteAndStartNavigation(routes.first())
                }

                override fun onRoutesRequestFailure(
                    throwable: Throwable,
                    routeOptions: RouteOptions
                ) {
                    // no impl
                }

                override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
                    // no impl
                }
            }
        )
    }

    private fun setRouteAndStartNavigation(route: DirectionsRoute) {
        // set route
        mapboxNavigation.setRoutes(listOf(route))

        // show UI elements
        findViewById<MapboxSoundButton>(R.id.soundButton).visibility = VISIBLE
        findViewById<MapboxRouteOverviewButton>(R.id.routeOverview).visibility = VISIBLE
        findViewById<CardView>(R.id.tripProgressCard).visibility = VISIBLE
        findViewById<MapboxRouteOverviewButton>(R.id.routeOverview).showTextAndExtend(2000L)
        findViewById<MapboxSoundButton>(R.id.soundButton).unmuteAndExtend(2000L)

        // move the camera to overview when new route is available
        navigationCamera.requestNavigationCameraToOverview()
    }

    private fun clearRouteAndStopNavigation() {
        // clear
        mapboxNavigation.setRoutes(listOf())

        // hide UI elements
        findViewById<MapboxSoundButton>(R.id.soundButton).visibility = INVISIBLE
        findViewById<MapboxManeuverView>(R.id.maneuverView).visibility = INVISIBLE
        findViewById<MapboxRouteOverviewButton>(R.id.routeOverview).visibility = INVISIBLE
        findViewById<CardView>(R.id.tripProgressCard).visibility = INVISIBLE
    }
}
