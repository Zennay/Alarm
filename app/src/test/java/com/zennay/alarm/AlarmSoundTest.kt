package com.zennay.alarm

import org.junit.Assert.*
import org.junit.Test

class AlarmSoundTest {
    @Test fun builtInSignalsContainAudibleSamples() {
        for (choice in listOf(
            AlarmSound.DOUBLE_BEEP, AlarmSound.SIREN, AlarmSound.URGENT
        )) {
            val tone = AlarmAudioPlayer.makeTone(choice)
            assertEquals(4 * 22050, tone.size)
            assertTrue("No audio samples for $choice", tone.any { it != 0.toShort() })
            assertTrue("No rests in $choice", tone.any { it == 0.toShort() })
            assertTrue("PCM samples clipped", tone.maxOrNull()!! < Short.MAX_VALUE)
            assertTrue("PCM samples clipped", tone.minOrNull()!! > Short.MIN_VALUE)
        }
    }

    @Test fun alarmPresetsAreDifferent() {
        val doubleBeep = AlarmAudioPlayer.makeTone(AlarmSound.DOUBLE_BEEP)
        val siren = AlarmAudioPlayer.makeTone(AlarmSound.SIREN)
        val urgent = AlarmAudioPlayer.makeTone(AlarmSound.URGENT)
        assertFalse(doubleBeep.contentEquals(siren))
        assertFalse(doubleBeep.contentEquals(urgent))
        assertFalse(siren.contentEquals(urgent))
    }
}
