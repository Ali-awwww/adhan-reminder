package com.adhanreminder.app.device

import org.junit.Assert.assertEquals
import org.junit.Test

class ManufacturerMappingTest {
    @Test fun mapsKnownManufacturers() {
        assertEquals(ManufacturerGroup.XIAOMI, ManufacturerMapping.groupFor("Redmi"))
        assertEquals(ManufacturerGroup.HUAWEI, ManufacturerMapping.groupFor("HONOR"))
        assertEquals(ManufacturerGroup.SAMSUNG, ManufacturerMapping.groupFor("samsung"))
        assertEquals(ManufacturerGroup.OPPO, ManufacturerMapping.groupFor("realme"))
        assertEquals(ManufacturerGroup.VIVO, ManufacturerMapping.groupFor("iQOO"))
        assertEquals(ManufacturerGroup.PIXEL, ManufacturerMapping.groupFor("google"))
        assertEquals(ManufacturerGroup.GENERIC, ManufacturerMapping.groupFor("unknown"))
    }
}
