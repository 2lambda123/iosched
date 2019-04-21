/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.samples.apps.iosched.model.SessionId
import com.google.samples.apps.iosched.model.SpeakerId
import com.google.samples.apps.iosched.shared.analytics.AnalyticsActions
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper
import com.google.samples.apps.iosched.shared.di.SearchUsingRoomEnabledFlag
import com.google.samples.apps.iosched.shared.domain.search.SearchDbUseCase
import com.google.samples.apps.iosched.shared.domain.search.SearchUseCase
import com.google.samples.apps.iosched.shared.domain.search.Searchable
import com.google.samples.apps.iosched.shared.domain.search.Searchable.SearchedSession
import com.google.samples.apps.iosched.shared.domain.search.Searchable.SearchedSpeaker
import com.google.samples.apps.iosched.shared.result.Event
import com.google.samples.apps.iosched.shared.result.Result
import com.google.samples.apps.iosched.shared.result.successOr
import com.google.samples.apps.iosched.shared.util.map
import com.google.samples.apps.iosched.ui.search.SearchResultType.CODELAB
import com.google.samples.apps.iosched.ui.search.SearchResultType.SESSION
import com.google.samples.apps.iosched.ui.search.SearchResultType.SPEAKER
import timber.log.Timber
import javax.inject.Inject

class SearchViewModel @Inject constructor(
    private val analyticsHelper: AnalyticsHelper,
    private val loadSearchResultsUseCase: SearchUseCase,
    private val loadDbSearchResultsUseCase: SearchDbUseCase
) : ViewModel(), SearchResultActionHandler {

    @Inject
    @JvmField
    @SearchUsingRoomEnabledFlag
    var searchUsingRoomFeatureEnabled: Boolean = false

    private val _navigateToSessionAction = MutableLiveData<Event<SessionId>>()
    val navigateToSessionAction: LiveData<Event<SessionId>> = _navigateToSessionAction

    private val _navigateToSpeakerAction = MutableLiveData<Event<SpeakerId>>()
    val navigateToSpeakerAction: LiveData<Event<SpeakerId>> = _navigateToSpeakerAction

    private val loadSearchResults = MutableLiveData<Result<List<Searchable>>>()
    val searchResults: LiveData<List<SearchResult>>

    val isEmpty: LiveData<Boolean>

    init {
        searchResults = loadSearchResults.map {
            val result = it as? Result.Success ?: return@map emptyList<SearchResult>()
            result.data.map { searched ->
                when (searched) {
                    is SearchedSession -> {
                        val session = searched.session
                        SearchResult(
                            session.title,
                            session.type.displayName,
                            SearchResultType.SESSION,
                            session.id
                        )
                    }
                    is SearchedSpeaker -> {
                        val speaker = searched.speaker
                        SearchResult(
                            speaker.name,
                            "Speaker",
                            SearchResultType.SPEAKER,
                            speaker.id
                        )
                    }
                }
            }
        }

        isEmpty = loadSearchResults.map {
            it.successOr(null).isNullOrEmpty()
        }
    }

    override fun openSearchResult(searchResult: SearchResult) {
        when (searchResult.type) {
            SESSION -> {
                val sessionId = searchResult.objectId
                analyticsHelper.logUiEvent("Session: $sessionId",
                    AnalyticsActions.SEARCH_RESULT_CLICK)
                _navigateToSessionAction.value = Event(sessionId)
            }
            SPEAKER -> {
                val speakerId = searchResult.objectId
                analyticsHelper.logUiEvent("Speaker: $speakerId",
                    AnalyticsActions.SEARCH_RESULT_CLICK)
                _navigateToSpeakerAction.value = Event(speakerId)
            }
            CODELAB -> {
                // not implemented
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        if (newQuery.length < 2) {
            return
        }
        analyticsHelper.logUiEvent("Query: $newQuery", AnalyticsActions.SEARCH_QUERY_SUBMIT)
        executeSearch(newQuery)
    }

    private fun executeSearch(query: String) {
        if (searchUsingRoomFeatureEnabled) {
            Timber.d("Searching for query using Room: $query")
            loadDbSearchResultsUseCase(query, loadSearchResults)
        } else {
            Timber.d("Searching for query without using Room: $query")
            loadSearchResultsUseCase(query, loadSearchResults)
        }
    }
}
