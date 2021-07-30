package com.mapbox.w3w.sample.ui.common

import com.mapbox.search.result.SearchAddress
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.what3words.javawrapper.response.Suggestion

enum class ResultType {
    W3W_SUGGESTION, MAPBOX_SUGGESTION, MAPBOX_REVERSE_GEOCODE
}

abstract class SimpleAdapterResult(val type: ResultType) {
    abstract fun getDisplayTitle(): String
}

class W3wSuggestionResult(val item: Suggestion) : SimpleAdapterResult(ResultType.W3W_SUGGESTION) {
    override fun getDisplayTitle(): String {
        return item.words
    }
}

class MapBoxResult(val item: SearchSuggestion) : SimpleAdapterResult(ResultType.MAPBOX_SUGGESTION) {
    override fun getDisplayTitle(): String {
        return item.descriptionText?: item.name
    }
}

class ReverseGeocodeResult(val item: SearchResult) : SimpleAdapterResult(ResultType.MAPBOX_REVERSE_GEOCODE) {
    override fun getDisplayTitle(): String {
        if (item.address == null) {
            return item.descriptionText?: item.name
        }
        return item.address!!.formattedAddress(
            SearchAddress.FormatStyle.Custom(
                SearchAddress.FormatComponent.HOUSE_NUMBER,
                SearchAddress.FormatComponent.STREET,
                SearchAddress.FormatComponent.PLACE,
                SearchAddress.FormatComponent.REGION,
                SearchAddress.FormatComponent.POSTCODE
            )
        )?: item.descriptionText?: item.name
    }
}