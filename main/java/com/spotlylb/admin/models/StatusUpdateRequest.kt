package com.spotlylb.admin.models

import com.google.gson.annotations.SerializedName

data class StatusUpdateRequest(
    @SerializedName("status")
    val status: String
)