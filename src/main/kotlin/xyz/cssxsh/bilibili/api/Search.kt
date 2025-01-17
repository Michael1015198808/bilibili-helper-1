package xyz.cssxsh.bilibili.api

import io.ktor.client.request.*
import xyz.cssxsh.bilibili.*
import xyz.cssxsh.bilibili.data.*

/**
 * @param order 粉丝数：fans; 用户等级：level
 * @param type 全部用户：0; up主：1; 普通用户：2; 认证用户：3
 */
suspend fun BiliClient.searchUser(
    keyword: String,
    order: String? = null,
    asc: Boolean = false,
    type: Int = 0,
    page: Int = 1,
    url: String = SEARCH_TYPE
): SearchResult<SearchUser> = json(url) {
    parameter("keyword", keyword)
    parameter("search_type", SearchType.USER.value)
    parameter("order", order)
    parameter("order_sort", if (asc) 1 else 0)
    parameter("user_type", type)
    parameter("page", page)
}

suspend fun BiliClient.searchBangumi(
    keyword: String,
    page: Int = 1,
    url: String = SEARCH_TYPE
): SearchResult<SearchSeason> = json(url) {
    parameter("keyword", keyword)
    parameter("search_type", SearchType.BANGUMI.value)
    parameter("page", page)
}

suspend fun BiliClient.searchFT(
    keyword: String,
    page: Int = 1,
    url: String = SEARCH_TYPE
): SearchResult<SearchSeason> = json(url) {
    parameter("keyword", keyword)
    parameter("search_type", SearchType.FILM_AND_TELEVISION.value)
    parameter("page", page)
}