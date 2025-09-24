package org.edu_sharing.rendering.core.dto

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls


data class RequestUserData(
    val authorityName: String,
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val firstName: String? = "",
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val surName: String? = "",
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val userEMail: String? = ""
)
