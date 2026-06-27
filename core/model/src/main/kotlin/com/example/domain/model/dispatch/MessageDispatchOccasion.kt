package com.example.domain.model.dispatch

import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.OccasionType

data class MessageDispatchOccasion(
    val occasionId: OccasionId?,
    val occasionType: OccasionType,
    val occasionLabel: String?,
)
