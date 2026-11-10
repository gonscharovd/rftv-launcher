package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class RftvWeatherRepositoryTest {

    @Test
    fun openMeteoWeatherCodesHaveRussianDescriptions() {
        assertEquals("Ясно", RftvWeatherRepository.weatherDescription(0))
        assertEquals("Переменная облачность", RftvWeatherRepository.weatherDescription(2))
        assertEquals("Снег", RftvWeatherRepository.weatherDescription(73))
        assertEquals("Гроза", RftvWeatherRepository.weatherDescription(95))
    }

    @Test
    fun metNorwaySymbolsMapToWmoCodes() {
        assertEquals(0, RftvWeatherRepository.metCode("clearsky_day"))
        assertEquals(2, RftvWeatherRepository.metCode("partlycloudy_day"))
        assertEquals(61, RftvWeatherRepository.metCode("lightrain"))
        assertEquals(82, RftvWeatherRepository.metCode("heavyshowers_day"))
        assertEquals(95, RftvWeatherRepository.metCode("thunderstorm"))
    }

    @Test
    fun wttrCodesMapToWmoCodes() {
        assertEquals(0, RftvWeatherRepository.wwoCode(113))
        assertEquals(2, RftvWeatherRepository.wwoCode(116))
        assertEquals(45, RftvWeatherRepository.wwoCode(248))
        assertEquals(73, RftvWeatherRepository.wwoCode(332))
        assertEquals(95, RftvWeatherRepository.wwoCode(386))
    }
}
