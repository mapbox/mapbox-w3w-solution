package com.mapbox.w3w.sample.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.geojson.Point
import com.mapbox.search.MapboxSearchSdk
import com.mapbox.search.ResponseInfo
import com.mapbox.search.ReverseGeoOptions
import com.mapbox.search.ReverseGeocodingSearchEngine
import com.mapbox.search.SearchCallback
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchRequestTask
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.w3w.sample.R
import com.mapbox.w3w.sample.ui.common.AdapterOnClickListener
import com.mapbox.w3w.sample.ui.common.MapBoxResult
import com.mapbox.w3w.sample.ui.common.ResultType.MAPBOX_REVERSE_GEOCODE
import com.mapbox.w3w.sample.ui.common.ResultType.MAPBOX_SUGGESTION
import com.mapbox.w3w.sample.ui.common.ResultType.W3W_SUGGESTION
import com.mapbox.w3w.sample.ui.common.ResultsAdapter
import com.mapbox.w3w.sample.ui.common.ReverseGeocodeResult
import com.mapbox.w3w.sample.ui.common.SimpleAdapterResult
import com.mapbox.w3w.sample.ui.common.W3wSuggestionResult
import com.mapbox.w3w.sample.ui.map.EXTRA_MAP_POINT
import com.mapbox.w3w.sample.ui.map.MapActivity
import com.mapbox.w3w.sample.ui.navigation.EXTRA_NAVIGATION_POINT
import com.mapbox.w3w.sample.ui.navigation.NavigationActivity
import com.mapbox.w3w.sample.util.LocationPermissionsHelper
import com.what3words.androidwrapper.What3WordsV3
import com.what3words.javawrapper.request.Coordinates
import com.what3words.javawrapper.response.Autosuggest
import com.what3words.javawrapper.response.ConvertToCoordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.regex.Pattern

class MainActivity : AppCompatActivity(), PermissionsListener, AdapterOnClickListener {

    private val wrapper by lazy {
        What3WordsV3(getString(R.string.w3w_api_key), this)
    }

    var currentLat: Double = 0.0
    var currentLon: Double = 0.0

    private val locationEngineCallback = LocationListeningCallback(this)
    private val permissionsHelper = LocationPermissionsHelper(this)

    //Mapbox search
    private lateinit var searchEngine: SearchEngine
    private lateinit var reverseEngine: ReverseGeocodingSearchEngine
    private lateinit var searchRequestTask: SearchRequestTask

    //Results Adapter
    private lateinit var resultsRecycleView: RecyclerView
    private lateinit var resultsAdapter: ResultsAdapter

    private val regex =
        "^/*[^0-9`~!@#$%^&*()+\\-_=\\]\\[{\\}\\\\|'<,.>?/\";:£§º©®\\s]{1,}[.｡。･・︒។։။۔።।][^0-9`~!@#$%^&*()+\\-_=\\]\\[{\\}\\\\|'<,.>?/\";:£§º©®\\s]{1,}[.｡。･・︒។։။۔።।][^0-9`~!@#$%^&*()+\\-_=\\]\\[{\\}\\\\|'<,.>?/\";:£§º©®\\s]{1,}$"
    val pattern: Pattern = Pattern.compile(regex)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (LocationPermissionsHelper.areLocationPermissionsGranted(this)) {
            onLocationPermissionGranted()
            requestPermissionIfNotGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            permissionsHelper.requestLocationPermissions(this)
        }

        searchEngine = MapboxSearchSdk.createSearchEngine()
        reverseEngine = MapboxSearchSdk.createReverseGeocodingSearchEngine()

        resultsRecycleView = findViewById<View>(R.id.recycler_view_results) as RecyclerView
        resultsRecycleView.layoutManager = LinearLayoutManager(this)
        resultsAdapter = ResultsAdapter(arrayListOf(), this)
        resultsRecycleView.addItemDecoration(
            DividerItemDecoration(
                resultsRecycleView.context,
                (resultsRecycleView.layoutManager as LinearLayoutManager).orientation
            )
        )
        resultsRecycleView.adapter = resultsAdapter

        val textInput: EditText = findViewById(R.id.textInputString)
        //Setting up the search field
        textInput.setOnEditorActionListener { v, actionId, event ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_UNSPECIFIED -> {
                    doSearch(textInput.text.toString())
                    false
                }
                else -> false
            }
        }

        val buttonSearch: Button = findViewById(R.id.buttonSearch)
        buttonSearch.setOnClickListener {
            doSearch(textInput.text.toString())
        }

        val buttonReverse: Button = findViewById(R.id.buttonReverse)
        val longField: TextInputEditText = findViewById(R.id.textInputLongitude)
        val latField: TextInputEditText = findViewById(R.id.textInputLatitude)

        buttonReverse.setOnClickListener {
            reverseSearch(longField.text.toString().toDouble(), latField.text.toString().toDouble(), reverseCallback)
        }
    }

    private fun doSearch(searchString: String) {
        val matcher = pattern.matcher(searchString)

        if (matcher.find()) {
            //This is a w3w address
            getWhat3WordsSuggestions(searchString, w3wSuggestionCallback = { result ->
                //use Dispatcher.Main to update your views with the results - Main thread
                if (result.isSuccessful && result.suggestions.count() != 0) {
                    resultsAdapter.clear()
                    result.suggestions.forEach { suggestion ->
                        resultsAdapter.apply {
                            addEntry((W3wSuggestionResult(suggestion)))
                            notifyDataSetChanged()
                        }
                    }
                } else {
                    if (result.suggestions.count() == 0) {
                        println("No result for this search")
                    } else {
                        println(result.error.message)
                    }
                }
            })
        } else {
            //This is a Mapbox search
            resultsAdapter.clear()
            searchMapbox(searchString, searchCallback)
        }
    }

    /**
     * Get w3w suggestions for partial 3 words
     *
     * @param textInput 3 words (ex. clip.apples.l in hope of suggestion clip.apples.leap)
     * @param w3wSuggestionCallback function to call when receiving results, with ConvertToCoordinates param
     */
    private fun getWhat3WordsSuggestions(textInput: String, w3wSuggestionCallback: (Autosuggest) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            //use wrapper.autosuggest() with Dispatcher.IO - background thread
            println("Looking for w3w suggestions for $textInput centered around lat=$currentLat lon=$currentLon")
            val result = wrapper.autosuggest(textInput).focus(
                Coordinates(currentLat, currentLon)
            ).execute()
            CoroutineScope(Dispatchers.Main).launch {
                w3wSuggestionCallback(result)
            }
        }
    }

    /**
     * Make a w3w coordinates search
     *
     * @param textInput 3 words (ex. clip.apples.leap)
     * @param w3wCallback function to call when receiving results, with ConvertToCoordinates param
     */
    private fun searchWhat3Words(textInput: String, w3wCallback: (ConvertToCoordinates) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            //use wrapper.convertToCoordinates() with Dispatcher.IO - background thread
            val result =
                wrapper.convertToCoordinates(textInput)
                    .execute()
            CoroutineScope(Dispatchers.Main).launch {
                w3wCallback(result)
            }
        }
    }

    /**
     * Make a Mapbox coordinates search
     *
     * @param textInput search text
     * @param searchCallback callback for results
     */
    private fun searchMapbox(textInput: String, searchCallback: SearchSelectionCallback) {
        searchRequestTask = searchEngine.search(
            textInput,
            SearchOptions(limit = 3),
            searchCallback
        )
    }

    /**
     * Make a Mapbox reverse geo search
     *
     * @param searchCallback callback for results
     */
    private fun reverseSearch(long: Double, lat: Double, searchCallback: SearchCallback) {
        searchRequestTask = reverseEngine.search(
            ReverseGeoOptions(Point.fromLngLat(long, lat)),
            searchCallback
        )
    }

    private val searchCallback = object : SearchSelectionCallback {
        override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
            if (suggestions.isEmpty()) {
                Log.i("SearchApiExample", "No suggestions found")
            } else {
                resultsAdapter.clear()
                suggestions?.forEach { suggestion ->
                    resultsAdapter.apply {
                        addEntry((MapBoxResult(suggestion)))
                        notifyDataSetChanged()
                    }
                }
            }
        }

        override fun onResult(
            suggestion: SearchSuggestion,
            result: SearchResult,
            responseInfo: ResponseInfo
        ) {
            Log.i("SearchApiExample", "Search result: $result")
            result.coordinate?.let { launchCoordinates(it)}
        }

        override fun onCategoryResult(
            suggestion: SearchSuggestion,
            results: List<SearchResult>,
            responseInfo: ResponseInfo
        ) {
            Log.i("SearchApiExample", "Category search results: $results")
        }

        override fun onError(e: Exception) {
            Log.i("SearchApiExample", "Search error", e)
        }
    }

    private val reverseCallback = object : SearchCallback {
        override fun onResults(results: List<SearchResult>, responseInfo: ResponseInfo) {
            if (results.isEmpty()) {
                Log.i("SearchApiExample", "No reverse geocode result")
            } else {
                resultsAdapter.clear()
                results.take(3).forEach { result ->
                    resultsAdapter.apply {
                        addEntry((ReverseGeocodeResult(result)))
                        notifyDataSetChanged()
                    }
                }
            }
        }

        override fun onError(e: Exception) {
            Log.i("SearchApiExample", "Reverse search error", e)
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Toast.makeText(
            this,
            "This app needs location and storage permissions in order to show its functionality.",
            Toast.LENGTH_LONG
        ).show()
    }

    @SuppressLint("MissingPermission")
    fun onLocationPermissionGranted() {
        val DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L
        val DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5

        val locationEngine = LocationEngineProvider.getBestLocationEngine(this)
        val request = LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
            .setPriority(LocationEngineRequest.PRIORITY_NO_POWER)
            .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME)
            .build()

        locationEngine.requestLocationUpdates(request, locationEngineCallback, mainLooper)
        locationEngine.getLastLocation(locationEngineCallback)
    }

    @SuppressLint("MissingPermission")
    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            onLocationPermissionGranted()
            requestPermissionIfNotGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            Toast.makeText(
                this,
                "You didn't grant location permissions.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestPermissionIfNotGranted(permission: String) {
        val permissionsNeeded: MutableList<String> = ArrayList()
        if (
            ContextCompat.checkSelfPermission(this, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(permission)
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                10
            )
        } else {
            //TODO
        }
    }

    private class LocationListeningCallback internal constructor(activity: MainActivity) : LocationEngineCallback<LocationEngineResult> {

        private val activityWeakReference: WeakReference<MainActivity>

        init {this.activityWeakReference = WeakReference(activity)}

        override fun onSuccess(result: LocationEngineResult?) {
            val location = result?.lastLocation
            val activity = activityWeakReference.get()
            if (location != null && activity != null) {
                activity.currentLat = location.latitude
                activity.currentLon = location.longitude
            }
        }

        /**
         * The LocationEngineCallback interface's method which fires when the device's location can not be captured
         *
         * @param exception the exception message
         */
        override fun onFailure(exception: Exception) {
            // The LocationEngineCallback interface's method which fires when the device's location can not be captured
        }
    }

    override fun onDestroy() {
        searchRequestTask.cancel()
        super.onDestroy()
    }


    override fun onItemClicked(item: SimpleAdapterResult) {
        when (item.type) {
            W3W_SUGGESTION -> launchWhat3Words(item as W3wSuggestionResult)
            MAPBOX_SUGGESTION -> selectSuggestion(item as MapBoxResult)
            MAPBOX_REVERSE_GEOCODE -> launchCoordinates((item as ReverseGeocodeResult).item.coordinate)
            else -> {
                //TODO
            }
        }
    }

    private fun launchWhat3Words(what3Words: W3wSuggestionResult) {
        searchWhat3Words(what3Words.getDisplayTitle(), w3wCallback = { result ->
            //use Dispatcher.Main to update your views with the results - Main thread
            if (result.isSuccessful) {
                launchCoordinates(Point.fromLngLat(result.coordinates.lng, result.coordinates.lat))
            } else {
                println(result.error.message)
            }
        })
    }

    private fun launchCoordinates(destination: Point?) {
        val dialogClickListener =
            DialogInterface.OnClickListener { dialog, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        launchLocationIntoMap(destination)
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        launchLocationIntoNavigation(destination)
                    }
                }
            }

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setMessage(getString(R.string.question_action)).setPositiveButton(getString(R.string.title_map), dialogClickListener)
            .setNegativeButton(getString(R.string.title_navigation), dialogClickListener).show()
    }

    private fun selectSuggestion(mapBoxSuggestion: MapBoxResult) {
        searchEngine.select(mapBoxSuggestion.item, searchCallback)
    }

    private fun launchLocationIntoMap(coordinate: Point?) {
        if (coordinate == null) {
            println("No coordinate attached to this result!!")
        }
        else {
            val intent = Intent(this, MapActivity::class.java).apply {
                putExtra(EXTRA_MAP_POINT, coordinate)
            }
            startActivity(intent)
        }
    }

    private fun launchLocationIntoNavigation(coordinate: Point?) {
        if (coordinate == null) {
            println("No coordinate attached to this result!!")
        }
        else {
            val intent = Intent(this, NavigationActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATION_POINT, coordinate)
            }
            startActivity(intent)
        }
    }
}