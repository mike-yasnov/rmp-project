package com.highloadinvest.banking.domain.services

interface QuoteSnapshotProvider {
    suspend fun snapshot(): Map<String, Double>
}
