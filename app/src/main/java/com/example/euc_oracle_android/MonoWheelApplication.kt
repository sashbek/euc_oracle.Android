package com.example.euc_oracle_android

import android.app.Application

class MonoWheelApplication : Application() {

    companion object {
        lateinit var instance: MonoWheelApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}