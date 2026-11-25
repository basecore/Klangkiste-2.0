package de.basecore.klangkiste2.core
import org.junit.Assert.assertEquals
import org.junit.Test
class CueParserTest {
 @Test fun parsesTitleAndFrames(){
  val text="""
TRACK 01 AUDIO
 TITLE "Intro"
 INDEX 01 00:00:00
TRACK 02 AUDIO
 TITLE "Story"
 INDEX 01 01:02:37
""".trimIndent()
  val result=CueParser.parse(text)
  assertEquals(2,result.size)
  assertEquals("Intro",result[0].title)
  assertEquals(62.0+37.0/75.0,result[1].timeSeconds,0.001)
 }
}
