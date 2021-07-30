# Mapbox + what3words

![Mapbox + w3w example](docs/mapbox-w3w.gif)

This example code can be used to create an Android application including [what3words](https://what3words.com/clip.apples.leap) search as well as Mapbox Search. Users can select a search result and display it on a map or navigate to it.

This repository includes dependencies to the following:
* [what3words](https://developer.what3words.com/tutorial/android): search suggestions (3-word suggestion for partial search string) and search results (location for full 3-word string)
* [Mapbox Search SDK for Android](https://docs.mapbox.com/android/search/guides/): Search (geocoding) and reverse geocoding requests (MainActivity)
* [Mapbox Map SDK for Android v10 (rc1)](https://docs.mapbox.com/android/beta/maps/guides/): display a search result on a map (MapActivity)
* [Mapbox Navigation SDK for Android v2 (Beta 15)](https://docs.mapbox.com/android/beta/navigation/guides/): navigate to search result (NavigationActivity)

If you have a usage question pertaining to a Mapbox SDK for Android, or any of our other products, contact us through [our support page](https://www.mapbox.com/contact/).

# Installation

1. You will need a [Mapbox account](https://account.mapbox.com/) and a [what3words API key](https://accounts.what3words.com/create-api-key?referrer=/tutorial/android) to run this project.

2. Add a `MAPBOX_DOWNLOADS_TOKEN` gradle property or env variable with a **secret token**. The token needs to have the `DOWNLOADS:READ` scope. You can obtain the token from your [Mapbox Account page](https://account.mapbox.com/access-tokens/).

3. You will also need a [**public Mapbox token**](https://account.mapbox.com/) and a **what3words API Key**. This project has both of those declared as string values inside `YOUR_APP_MODULE_NAME/src/main/res/values/developer-config.xml`, a file that is added to this project `.gitignore`.

4. For more information on token security best practices, please visit [Mapbox online documentation](https://docs.mapbox.com/help/troubleshooting/private-access-token-android-and-ios/).

5. Full build instructions (API level 21, Java SDK 8) are available in this [Installation section](https://docs.mapbox.com/android/beta/navigation/guides/install/) of each Mapbox SDK.

6. Your project level build.gradle should declare the Mapbox Downloads API's `v2/release/maven` endpoint:
```gradle
allprojects {
    repositories {
        google()
        jcenter()
        maven {
            url 'https://api.mapbox.com/downloads/v2/releases/maven'
            authentication {
                basic(BasicAuthentication)
            }
            credentials {
                // Do not change the username below.
                // This should always be `mapbox` (not your username).
                username = "mapbox"
                // This sets `password` to reference the secret token you stored in gradle.properties or env:
                password = project.properties['MAPBOX_DOWNLOADS_TOKEN'] ?: ""
            }
        }
    }
}
```

7. The Mapbox Maps SDK, Mapbox Search SDK and what3words Android wrapper are declared in the module-level build.gradle file. Mapbox Maps SDK is a transient depedency.
```gradle
// Mapbox
implementation 'com.mapbox.navigation:android:2.0.0-beta.15'
implementation 'com.mapbox.search:mapbox-search-android:1.0.0-beta.14'
implementation 'com.mapbox.mapboxsdk:mapbox-android-plugin-annotation-v9:0.8.0'

// W3W
implementation 'com.what3words:w3w-android-wrapper:3.1.9'
```
Please refer to the respective SDK pages for release notes as they are near Release Candidates versions.

 * [Mapbox Navigation SDK for Android release notes](https://github.com/mapbox/mapbox-navigation-android/releases)
 * [Mapbox Search SDK for Android documentation](https://docs.mapbox.com/android/search/guides/)
 * [Mapbox Maps SDK for Android change log](https://github.com/mapbox/mapbox-maps-android/blob/main/CHANGELOG.md)

1. Finally, we need the following permissions:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

# what3words Search

A what3words search string is identified by a regexp of the form `"word.word.x"`.
```kotlin
private val regex =
   "^/*[^0-9`~!@#$%^&*()+\\-_=\\]\\[{\\}\\\\|'<,.>?/\";:£§º©®\\s]{1,}[.｡。･・︒។։။۔።।][^0-9`~!@#$%^&*()+\\-_=\\]\\[{\\}\\\\|'<,.>?/\";:£§º©®\\s]{1,}[.｡。･・︒។։။۔።।][^0-9`~!@#$%^&*()+\\-_=\\]\\[{\\}\\\\|'<,.>?/\";:£§º©®\\s]{1,}$"
val pattern: Pattern = Pattern.compile(regex)
val matcher = pattern.matcher(searchString)
if (matcher.find()) {
   // This is a w3w address.
```

With any number of letters in the 3 word, the search string can be passed to the android what3words wrapper to retrieve suggestions, priotitized by closeness to current location if provided.

```kotlin
CoroutineScope(Dispatchers.IO).launch {
   val result = wrapper.autosuggest(textInput).focus(
       Coordinates(currentLat, currentLon)
   ).execute()
   CoroutineScope(Dispatchers.Main).launch {
       w3wSuggestionCallback(result)
   }
}
```

When a user selects a suggestion (or when you have otherwise identified a full three word address), one more call is required to get actual coordinates.

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val result =
        wrapper.convertToCoordinates(textInput)
            .execute()
    CoroutineScope(Dispatchers.Main).launch {
        w3wCallback(result)
    }
}
```

See [what3words Android documentation](https://developer.what3words.com/tutorial/android) for more details.

# Mapbox Search

Users can enter a street address or POI search string and the Search SDK will be used for geocoding requests.

```kotlin
searchEngine = MapboxSearchSdk.createSearchEngine()
searchRequestTask = searchEngine.search(textInput, SearchOptions(limit = 3),  searchCallback)
```

Select a suggestion and retrieve location information.

```kotlin
searchEngine.select(mapBoxSuggestion.item, searchCallback)
```

Reverse geocoding was also added to this sample code. To get location information on coordinates included in a what3words search result, use the following:

```kotlin
reverseEngine = MapboxSearchSdk.createReverseGeocodingSearchEngine()
searchRequestTask = reverseEngine.search(ReverseGeoOptions(Point.fromLngLat(long, lat)), searchCallback)
```

# Mapbox Map

MapActivity is based on this [Mapbox Map SDK v10 example](https://docs.mapbox.com/android/beta/maps/examples/default-point-annotation/), which creates a map and adds an annotation. MapActivity uses the location provided in the Intent coming from MainActivity for that annotation.

```kotlin
override fun onStyleLoaded(style: Style) {
    val point = intent.getSerializableExtra(EXTRA_MAP_POINT) as? Point
    point?.let {
        addAnnotationToMap(point.longitude(), point.latitude())
```

# Mapbox Navigation

NavigationActivity is based on the following [Mapbox Navigation SDK v2 example](https://docs.mapbox.com/android/beta/navigation/examples/basic/).

Destination is retrieved from Intent extras and used in `onCreate`

```kotlin
// Navigate to provided destination
val destination = intent.getSerializableExtra(EXTRA_NAVIGATION_POINT) as? Point
destination?.let {
        findRoute(destination)
}
```

Note that we are using a helper function `getLastKnownLocation()` in onCreate, when location has not been updated yet. This function scans location providers to get the most accurate last known location.
```kotlin
val origin = getLastKnownLocation().also {
            Log.d("NavigationActivity", "origin location: $it")
        } ?: return
```

This sample code also includes checks to ignore eroneous location updates.
```kotlin
// Some systems (especially Android emulator) will sometimes provide invalid locations.
if ((enhancedLocation.latitude == 0.0 && enhancedLocation.longitude == 0.0)
    || (enhancedLocation.latitude > 90.0)
    || (enhancedLocation.latitude < -90.0)
    || (enhancedLocation.longitude > 180.0)
    || (enhancedLocation.longitude < -180.0)
) {
    Log.e("NavigationActivity", "lat " + enhancedLocation.latitude + ", long " + enhancedLocation.longitude)
    return
}
```

# Authors

This solution was created by the [Mapbox Solutions Architecture team](https://www.mapbox.com/resources#solutions).

# Acknowledgements

 * Anton Baikou and Eugene Vabishchevich for their help with [Mapbox Dash](https://www.mapbox.com/dash) integration.
 * Zolzaya Chultembat and Tom Blaksley from what3words.
 * Chris Toomey, Zoltan Szabadi, Michael Robertson, Kyle Madsen and Yury Kanetski for their help creating this solution.
