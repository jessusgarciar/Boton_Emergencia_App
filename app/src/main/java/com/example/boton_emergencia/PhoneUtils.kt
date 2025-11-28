package com.example.boton_emergencia

object PhoneUtils {

    fun normalize(phoneNumber: String): String {
        return phoneNumber.filter { it.isDigit() }
    }

    fun validationMessage(normalizedPhoneNumber: String): String {
        return when {
            normalizedPhoneNumber.length < 10 -> "El número debe tener al menos 10 dígitos."
            normalizedPhoneNumber.length > 13 -> "El número es demasiado largo."
            else -> "" // valid
        }
    }

    fun formatPhoneNumberForWhatsApp(phoneNumber: String, countryCode: String = "52"): String {
        val digitsOnly = normalize(phoneNumber)
        if (digitsOnly.length == 10) {
            return countryCode + digitsOnly
        }
        return digitsOnly
    }
}
