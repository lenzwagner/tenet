package app.tenet.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyMeasurementsTest {
    @Test fun roundTrip() {
        val m = mapOf("waist" to 82.5f, "arm" to 36f)
        assertEquals(m, BodyMeasurements.decode(BodyMeasurements.encode(m)))
    }

    @Test fun emptyIsNull() = assertNull(BodyMeasurements.encode(mapOf("waist" to 0f)))

    @Test fun decodeNull() = assertEquals(emptyMap<String, Float>(), BodyMeasurements.decode(null))
}
