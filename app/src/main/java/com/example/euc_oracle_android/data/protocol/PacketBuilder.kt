package com.example.euc_oracle_android.data.protocol

import java.io.ByteArrayOutputStream

object PacketBuilder {
    private val WRITE_START_MARKER = byteArrayOf(0x55, 0xAA.toByte(), 0x55, 0xAA.toByte())
    private val WRITE_END_MARKER = byteArrayOf(0x55, 0xBB.toByte(), 0x55, 0xBB.toByte())

    // Управляющие регистры (CtrlReg)
    const val REG_READ_START_L = 0x00
    const val REG_READ_START_H = 0x01
    const val REG_READ_SIZE = 0x02
    const val REG_CMD = 0x03
    const val REG_STATUS = 0x04
    const val REG_VERSION = 0x05
    const val REG_DEVICE_TYPE = 0x06

    // Команды
    const val CMD_START_READ = 0x01.toByte()
    const val CMD_SAVE_TO_FLASH = 0x02.toByte()
    const val CMD_LOAD_FROM_FLASH = 0x03.toByte()
    const val CMD_RESET_DEFAULTS = 0x04.toByte()
    const val CMD_SOFT_RESET = 0x05.toByte()

    /**
     * Формирует пакет для записи одного регистра
     * Формат: [START_MARKER][ADDR_L][ADDR_H][SIZE_L][SIZE_H][DATA...][END_MARKER]
     */
    fun buildWritePacket(address: Int, data: ByteArray): ByteArray {
        val buffer = ByteArrayOutputStream()

        with(buffer) {
            write(WRITE_START_MARKER)

            // Адрес (2 байта, little-endian)
            write(address and 0xFF)
            write((address shr 8) and 0xFF)

            // Размер (2 байта, little-endian)
            write(data.size and 0xFF)
            write((data.size shr 8) and 0xFF)

            // Данные
            write(data)

            write(WRITE_END_MARKER)
        }

        return buffer.toByteArray()
    }

    /**
     * Формирует пакет для множественной записи регистров
     */
    fun buildWritePacket(registers: Map<Int, ByteArray>): ByteArray {
        val buffer = ByteArrayOutputStream()

        with(buffer) {
            write(WRITE_START_MARKER)

            registers.forEach { (address, data) ->
                write(address and 0xFF)
                write((address shr 8) and 0xFF)
                write(data.size and 0xFF)
                write((data.size shr 8) and 0xFF)
                write(data)
            }

            write(WRITE_END_MARKER)
        }

        return buffer.toByteArray()
    }

    /**
     * Формирует запрос на чтение данных
     * Алгоритм:
     * 1. Записываем READ_START_L
     * 2. Записываем READ_START_H
     * 3. Записываем READ_SIZE
     * 4. Записываем CMD = START_READ
     */
    fun buildReadRequest(startAddress: Int, size: Int): ByteArray {
        val registers = mutableMapOf<Int, ByteArray>()

        // READ_START_L
        registers[REG_READ_START_L] = byteArrayOf((startAddress and 0xFF).toByte())

        // READ_START_H
        registers[REG_READ_START_H] = byteArrayOf(((startAddress shr 8) and 0xFF).toByte())

        // READ_SIZE
        registers[REG_READ_SIZE] = byteArrayOf((size and 0xFF).toByte())

        // CMD = START_READ
        registers[REG_CMD] = byteArrayOf(CMD_START_READ)

        return buildWritePacket(registers)
    }

    /**
     * Формирует команду
     */
    fun buildCommand(command: Byte): ByteArray {
        return buildWritePacket(REG_CMD, byteArrayOf(command))
    }

    /**
     * Формирует команду сохранения в Flash
     */
    fun buildSaveToFlashCommand(): ByteArray = buildCommand(CMD_SAVE_TO_FLASH)

    /**
     * Формирует команду загрузки из Flash
     */
    fun buildLoadFromFlashCommand(): ByteArray = buildCommand(CMD_LOAD_FROM_FLASH)

    /**
     * Формирует команду сброса к заводским настройкам
     */
    fun buildResetDefaultsCommand(): ByteArray = buildCommand(CMD_RESET_DEFAULTS)
}
