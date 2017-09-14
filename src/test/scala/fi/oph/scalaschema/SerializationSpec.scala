package fi.oph.scalaschema

import java.sql.Timestamp
import java.time.{LocalDate, ZonedDateTime}
import java.util.Date

import org.joda.time.format.ISODateTimeFormat
import org.json4s.JsonAST.{JObject, JString}
import org.scalatest.{FreeSpec, Matchers}

import scala.reflect.runtime.{universe => ru}

class SerializationSpec extends FreeSpec with Matchers {
  "strings" in {
    testSerialization(Strings("a"), """{"s":"a"}""")
  }
  "numbers" in {
    testSerialization(Numbers(1, 1l, 0.4f, 1.1), """{"a":1,"b":1,"c":0.4000000059604645,"d":1.1}""")
  }

  "traits" in {
    testSerialization(ThingContainingTrait(Impl1("hello")), """{"x":{"x":"hello"}}""")
  }

  "dates" in {
    val dates = Dates(
      LocalDate.parse("2015-12-30"),
      ZonedDateTime.parse("1987-01-23T00:33:23Z"),
      Date.from(java.time.ZonedDateTime.parse("1977-03-13T13:42:11Z").toInstant),
      Timestamp.from(java.time.ZonedDateTime.parse("2007-08-23T10:43:21Z").toInstant),
      ISODateTimeFormat.dateTimeParser.withZoneUTC.parseDateTime("2017-09-13T12:43:21Z")
    )
    testSerialization(dates, """{"a":"2015-12-30","b":"1987-01-23T00:33:23Z","c":"1977-03-13T13:42:11Z","d":"2007-08-23T10:43:21Z","e":"2017-09-13T12:43:21Z"}""")
  }

  "booleans" in {
    testSerialization(Booleans(true), """{"field":true}""")
  }

  "lists" in {
    testSerialization(Lists(List(1)), """{"things":[1]}""")
  }

  "maps" in {
    testSerialization(Maps(Map("a" -> 1)), """{"things":{"a":1}}""")
  }

  "options" in {
    testSerialization(OptionalFields(None), """{}""")
    testSerialization(OptionalFields(Some(true)), """{"field":true}""")
  }

  "synthetic properties" in {
    testSerialization(WithSyntheticProperties(), """{"field2":[true],"field1":true}""")
  }

  "overridden synthetic properties" in {
    testSerialization(WithOverriddenSyntheticProperties(false), """{"field":false}""")
  }

  "empty optional" in {
    val json = Serializer.serialize(WithOptionalDiscriminator("name", None), defaultContext)
    json should equal(JObject("name" -> JString("name")))
  }

  "JValue field" in {
    testSerialization(WithJValue(JString("hello")), """{"x":"hello"}""")
  }

  "custom field filtering" in {
    def skipOtherThanA(s: ClassSchema, p: Property) = if (p.key == "a") List(p) else Nil
    testSerialization(Numbers(1, 1l, 0.4f, 1.1), """{"a":1}""", context = SerializationContext(SchemaFactory.default, propertyProcessor = skipOtherThanA))
  }

  def testSerialization[T](x: T, expected: String, context: SerializationContext = defaultContext)(implicit tag: ru.TypeTag[T]) = {
    val jValue = Serializer.serialize(x, context)
    org.json4s.jackson.JsonMethods.compact(jValue) should equal(expected)
  }

  private def defaultContext[T] =
    SerializationContext(SchemaFactory.default)
}

case class ThingContainingTrait(x: TraitsWithFields)
trait TraitsWithFields
case class Impl1(x: String) extends TraitsWithFields
case class Impl2(x: Int) extends TraitsWithFields
