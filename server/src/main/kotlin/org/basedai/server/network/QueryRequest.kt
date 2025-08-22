package org.basedai.server.network

import kotlinx.serialization.Serializable

@Serializable
data class QueryRequest(val question: String)