package org.basedai.server.network

import kotlinx.serialization.Serializable

@Serializable
data class QueryResponse(val answer: String)