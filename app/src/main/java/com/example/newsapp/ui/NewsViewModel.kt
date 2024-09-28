package com.example.newsapp.ui
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import android.net.NetworkCapabilities
import androidx.lifecycle.MutableLiveData

import com.example.newsapp.models.NewsResponse

import com.example.newsapp.models.Article
import kotlinx.coroutines.launch
import retrofit2.Response
import com.example.newsapp.repository.NewsRepository
import java.io.IOException
import androidx.lifecycle.viewModelScope
import com.example.newsapp.util.Resource


class NewsViewModel(app: Application, val newsRepository: NewsRepository): AndroidViewModel(app) {
    val headlines:MutableLiveData<Resource<NewsResponse>> = MutableLiveData()
    var headlinesPage = 1
    var headlinesResponse: NewsResponse? = null
    val searchNews:MutableLiveData<Resource<NewsResponse>> = MutableLiveData()
    var searchNewsPage = 1
    var searchNewsResponse: NewsResponse? = null
    var newSearchQuery:String? = null
    var oldSearchQuery:String? = null

    init{
        getHeadlines("us")
    }

    fun getHeadlines(countryCode: String) = viewModelScope.launch {
        headlinesInternet(countryCode)
    }
    fun searchNews(searchQuery: String) = viewModelScope.launch {
        searchNewsInternet(searchQuery)
    }

    private fun handleHeadlinesResponse(response: Response<NewsResponse>): Resource<NewsResponse>{
        if (response.isSuccessful){
            response.body()?.let { resultResponse ->
                headlinesPage++
                if (headlinesResponse == null){
                    headlinesResponse = resultResponse
                }
                else {
                    val oldArticles = headlinesResponse?.articles
                    val newArticles = resultResponse.articles
                    oldArticles?.addAll(newArticles)
                }
                return Resource.Success(headlinesResponse ?: resultResponse)
            }
        }
        return Resource.Error(response.message())
    }
    private fun handleSearchNewsResponse(response: Response<NewsResponse>): Resource<NewsResponse>{
        if (response.isSuccessful){
            response.body()?.let { resultResponse ->

                if (searchNewsResponse == null || newSearchQuery != oldSearchQuery){
                    searchNewsPage = 1
                    oldSearchQuery = newSearchQuery
                    searchNewsResponse = resultResponse
                }
                else {
                    searchNewsPage++
                    val oldArticles = searchNewsResponse?.articles
                    val newArticles = resultResponse.articles
                    oldArticles?.addAll(newArticles)
                }
                return Resource.Success(searchNewsResponse ?: resultResponse)
            }
        }
        return Resource.Error(response.message())
    }
    fun addToFavourites(article: Article) = viewModelScope.launch {
        newsRepository.upsert(article)
    }
    fun getFavouritesNews() = newsRepository.getFavouriteNews()
    fun deleteArticle(article: Article) = viewModelScope.launch {
        newsRepository.deleteArticle(article)
    }
    fun internetConnection(context: Context): Boolean{
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).apply {
            return getNetworkCapabilities(activeNetwork)?.run {
                when{
                    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                    else -> false
                }
            }?:false
        }
    }
    private suspend fun headlinesInternet(countryCode: String) {
        headlines.postValue(Resource.Loading())
        try {
            if (internetConnection(this.getApplication())) {
                val response = newsRepository.getHeadlines(countryCode, headlinesPage)
                headlines.postValue(handleHeadlinesResponse(response))
            } else {
                headlines.postValue(Resource.Error("No Internet Connection"))
            }
        }catch (t: Throwable){
            when(t){
                is IOException -> headlines.postValue(Resource.Error("Network Failure"))
                else -> headlines.postValue(Resource.Error("No signal"))
            }

        }
    }
    private suspend fun searchNewsInternet(searchQuery: String) {
        newSearchQuery = searchQuery
        searchNews.postValue(Resource.Loading())
        try {
            if (internetConnection(this.getApplication())) {
                val response = newsRepository.searchNews(searchQuery, searchNewsPage)

                searchNews.postValue((handleSearchNewsResponse(response)))
            } else {
                searchNews.postValue(Resource.Error("No Internet Connection"))
            }
        }catch(t:Throwable){
                when(t){
                    is IOException -> searchNews.postValue(Resource.Error("Network Failure"))
                    else -> searchNews.postValue(Resource.Error("No signal"))
                }
            }
    }




}