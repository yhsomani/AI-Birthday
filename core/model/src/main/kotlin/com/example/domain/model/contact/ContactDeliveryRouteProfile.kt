package com.example.domain.model.contact

import com.example.domain.model.MessageChannel

data class ContactDeliveryRouteProfile(
    val preferredChannel: MessageChannel,
    val hasPrimaryPhone: Boolean,
    val hasPrimaryEmail: Boolean,
)
