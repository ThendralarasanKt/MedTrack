package com.medtrack.app.data.care.model

import java.util.UUID

object CareIds {
    fun newId(): String = UUID.randomUUID().toString()
}
