package com.example.euc_oracle_android.data.protocol.models

object SystemRegs {
    const val DEVICE_ID = 0x10
    const val FW_VERSION = 0x14
    const val HW_REVISION = 0x18
    const val BOOT_COUNT = 0x1A
    const val UPTIME = 0x1E
    const val FREE_HEAP = 0x22
    const val LOG_LEVEL = 0x26
    const val DEVICE_NAME = 0x27
}

object WheelRegs {
    // ThirdEye
    const val THIRDEYE_ENABLE = 0x40
    const val THIRDEYE_NAME = 0x41
    const val THIRDEYE_TOP_COL = 0x4D
    const val THIRDEYE_BOTTOM_COL = 0x4E
    const val THIRDEYE_SHOW_NAMES = 0x4F
    const val THIRDEYE_BRIGHTNESS = 0x50
    const val THIRDEYE_AUTO_CONN = 0x51
    const val THIRDEYE_RECONN_INT = 0x52

    // UART
    const val UART_BAUD = 0x54
    const val UART_CONFIG = 0x58
    const val UART_PROTOCOL = 0x59

    // Calibration
    const val SPEED_FACTOR = 0x60
    const val SPEED_OFFSET = 0x62
    const val BATTERY_MIN_V = 0x64
    const val BATTERY_MAX_V = 0x66
    const val TEMP_OFFSET = 0x68

    // LED Proxy
    const val LED_SEND_ENABLE = 0x70
    const val LED_SEND_RATE = 0x71
    const val LED_PEER_MAC = 0x72

    // Dynamic
    const val SPEED = 0xC0
    const val PWM = 0xC2
    const val BATTERY = 0xC4
    const val TEMP = 0xC5
    const val DISTANCE = 0xC6
}

object LedRegs {
    // Main
    const val GLOBAL_BRIGHTNESS = 0x80
    const val POWER_LIMIT = 0x81
    const val FPS_TARGET = 0x83
    const val EFFECT_MODE = 0x84
    const val EFFECT_SPEED = 0x85
    const val EFFECT_INTENSITY = 0x86

    // Colors
    const val COLOR_PRIMARY = 0x90
    const val COLOR_SECONDARY = 0x94
    const val COLOR_SPEED_LOW = 0x98
    const val COLOR_SPEED_HIGH = 0x9C
    const val COLOR_BATTERY_LOW = 0xA0
    const val COLOR_BATTERY_HIGH = 0xA4

    // Thresholds
    const val SPEED_THRESHOLD_LOW = 0xB0
    const val SPEED_THRESHOLD_HIGH = 0xB2
    const val BATTERY_THRESHOLD_LOW = 0xB4
    const val BATTERY_THRESHOLD_HIGH = 0xB5

    // Behaviors
    const val HEADLIGHT_BEHAVIOR = 0xB8
    const val DRL_BEHAVIOR = 0xB9
    const val BUZZER_BEHAVIOR = 0xBA
    const val BUZZER_FLASH_COLOR = 0xBB
    const val BUZZER_DURATION = 0xBF

    // Inputs
    const val IN_HEADLIGHT = 0xE0
    const val IN_DRL = 0xE1
    const val IN_BUZZER = 0xE2
}