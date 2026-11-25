package de.basecore.klangkiste2.core
import org.junit.Assert.*
import org.junit.Test
class ImportFilterTest {
 @Test fun negativeTermExcludes() { assertFalse(ImportFilter.matches("Conni und Kater",4,"conni -kater")); assertTrue(ImportFilter.matches("Conni im Zoo",4,"conni -kater")) }
 @Test fun ageRangeWorks() { assertTrue(ImportFilter.matches("Titel",4,"",3,5)); assertFalse(ImportFilter.matches("Titel",7,"",null,5)) }
 @Test fun unknownAgeIsZero() { assertTrue(ImportFilter.matches("Titel",null,"",null,4)) }
}
