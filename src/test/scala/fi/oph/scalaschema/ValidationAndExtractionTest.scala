package fi.oph.scalaschema

import java.math.BigDecimal.{valueOf => bigDecimal}
import java.sql.Timestamp
import java.time.{LocalDate, ZonedDateTime}
import java.util.Date

import fi.oph.scalaschema.annotation.{Discriminator, EnumValue}
import fi.oph.scalaschema.extraction.{ValidationError, _}
import org.joda.time.format.ISODateTimeFormat
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods
import org.scalatest.{FreeSpec, Matchers}

import scala.reflect.runtime.{universe => ru}

class ValidationAndExtractionTest extends FreeSpec with Matchers {
  "Validation and extraction" - {
    "Simple example" - {
      "Extraction" in {
        val testValue = TestClass("name", List(1, 2, 3))
        verifyExtractionRoundTrip[TestClass](testValue)
      }
      "Missing fields validation" in {
        verifyValidation[TestClass](JObject(), Left(List(
          ValidationError("name",JNothing,MissingProperty()),
          ValidationError("stuff",JNothing,MissingProperty())
        )))
      }
      "Unexpected fields" - {
        val inputWithExtraField = JObject(("name" -> JString("john")), ("stuff" -> JArray(List(JInt(1)))), ("extra" -> JString("hello")))
        "Cause errors in default/strict mode" in {
          verifyValidation[TestClass](inputWithExtraField, Left(List(
            ValidationError("extra",JString("hello"),UnexpectedProperty())
          )))
        }
        "Are ignored if value is null" in {
          verifyValidation[TestClass](JObject(("name" -> JString("john")), ("stuff" -> JArray(List(JInt(1)))), ("extra" -> JNull)), Right(TestClass("john", List(1))), ExtractionContext(SchemaFactory.default))
        }
        "Are ignored in loose mode" in {
          verifyValidation[TestClass](inputWithExtraField, Right(TestClass("john", List(1))), ExtractionContext(SchemaFactory.default, ignoreUnexpectedProperties = true))
        }
      }
      "Field type validation" in {
        verifyValidation[TestClass](JObject(("name" -> JObject()), ("stuff", JArray(List(JString("a"), JString("b"))))), Left(List(
          ValidationError("name",JObject(),UnexpectedType("string")),
          ValidationError("stuff.0",JString("a"),UnexpectedType("number")),
          ValidationError("stuff.1",JString("b"),UnexpectedType("number"))
        )))
      }
    }
    "Strings" - {
      "Extracts string" in {
        verifyValidation[String](JString("1"), Right("1"))
      }
      "Accepts numeric input" in {
        verifyValidation[String](JInt(1), Right("1"))
      }
      "Accepts boolean input" in {
        verifyValidation[String](JBool(true), Right("true"))
      }
      "Nulls rejected" in {
        verifyValidation[String](JNull, Left(List(ValidationError("", JNull, UnexpectedType("string")))))
      }
      "Missing values rejected" in {
        verifyValidation[Strings](JObject(), Left(List(ValidationError("s", JNothing, MissingProperty()))))
      }
      "With default settings" - {
        "Empty strings allowed" in {
          verifyValidation[String](JString(""), Right(""))
        }
      }
      "When allowEmptyStrings=false" - {
        val emptyStringsNotAllowed = ExtractionContext(SchemaFactory.default, allowEmptyStrings = false)
        "Empty strings rejected" in {
          verifyValidation[String](JString(""), Left(List(ValidationError("", JString(""), EmptyString()))), emptyStringsNotAllowed)
        }
      }
    }
    "Dates" in {
      val dates = Dates(
        LocalDate.parse("2015-12-30"),
        ZonedDateTime.parse("1987-01-23T00:33:23Z"),
        Date.from(java.time.ZonedDateTime.parse("1977-03-13T13:42:11Z").toInstant),
        Timestamp.from(java.time.ZonedDateTime.parse("2007-08-23T10:43:21Z").toInstant),
        ISODateTimeFormat.dateTimeParser.withZoneUTC.parseDateTime("2017-09-13T12:43:21Z")
      )
      verifyExtractionRoundTrip(dates)
    }
    "Numbers" - {
      "As case class fields" in {
        verifyExtractionRoundTrip(Numbers(1, 1, 1, 1))
      }
      "Including BigDecimal, BigInt" in {
        verifyExtractionRoundTrip(MoreNumbers(1, 1, 1, 1, 1, 1))
      }
      "Java numbers" in {
        verifyExtractionRoundTrip(JavaNumbers(1, 1f, 1l, 1d, bigDecimal(1)))
      }
      "In lists" in {
        val result = verifyExtractionRoundTrip(MoreNumbersInLists(List(1), List(1), List(1), List(1), List(1), List(1)))
        // Force unboxing to make sure it works (didn't work before correct conversions in NumberExtractor)
        val i: Int = result.i.head
        val f: Float = result.f.head
        val l: Long = result.l.head
        val d: Double = result.d.head
        val bi: BigInt = result.bi.head
        val bd: BigDecimal = result.bd.head
      }
      "Optional" in {
        val result: OptionalNumbers = verifyExtractionRoundTrip(OptionalNumbers(Option(1), Option(1), Option(1), Option(1), Option(1), Option(1)))
        // Force unboxing to make sure it works (didn't work before correct conversions in NumberExtractor)
        val i: Int = result.i.get
        val f: Float = result.f.get
        val l: Long = result.l.get
        val d: Double = result.d.get
        val bi: BigInt = result.bi.get
        val bd: BigDecimal = result.bd.get
      }
      "Parsing from String value" - {
        "Valid value" in {
          verifyValidation[Numbers](JObject("a" -> JString("1"), "b" -> JString("1"), "c" -> JString("1"), "d" -> JString("1")), Right(Numbers(1, 1, 1, 1)))
        }
        "Invalid value" in {
          verifyValidation[Numbers](JObject("a" -> JString("LOL"), "b" -> JString("1"), "c" -> JString("1"), "d" -> JString("1")), Left(List(ValidationError("a", JString("LOL"), UnexpectedType("number")))))
        }
        "BigInt, BigDecimal" in {
          verifyValidation[BigNumbers](JObject("bi" -> JString("1234567890123456789012345678901234567890"), "bd" -> JString("1234567890123456789012345678901234567890.1234567890")),
            Right(BigNumbers(BigInt("1234567890123456789012345678901234567890"), BigDecimal("1234567890123456789012345678901234567890.1234567890"))))
        }
      }
    }
    "JValue fields" in {
      verifyExtractionRoundTrip(WithJValue(JString("boo")))
    }
    "Seqs" - {
      verifyValidation[Seq[String]](JArray(List(JString("HELLO"))), Right(List("HELLO")))
    }
    "Maps" - {
      "Map[String, Int]" in {
        verifyExtractionRoundTrip(Maps(Map("x" -> 1)))
      }
      "Only String supported as key type" in {
        val msg = intercept[IllegalArgumentException] { SchemaFactory.default.createSchema[Map[Int, String]] }.getMessage
        msg should equal("Maps are only supported with String keys")
      }
      "When validation of nested data fails" in {
        verifyValidation[Map[String, Int]](JObject(JField("first", JString("lol"))), Left(List(ValidationError("first", JString("lol"), UnexpectedType("number")))))
      }
    }
    "Optionals" - {
      "Missing field translates to None" in {
        verifyValidation[OptionalFields](JObject(), Right(OptionalFields(None)))
      }
      "Null value translates to None" in {
        verifyValidation[OptionalFields](JObject("field" -> JNull), Right(OptionalFields(None)))
      }
    }
    "@DefaultValue annotation" - {
      "Booleans" in {
        verifyValidation[BooleansWithDefault](JObject(), Right(BooleansWithDefault(true)))
      }
      "Strings" in {
        verifyValidation[NumbersWithDefault](JObject(), Right(NumbersWithDefault(1)))
      }
      "Numbers" in {
        verifyValidation[StringsWithDefault](JObject(), Right(StringsWithDefault("hello")))
      }
    }
    "@EnumValue annotation" - {
      "Successful for strings, optionals and lists" in {
        verifyExtractionRoundTrip(WithEnumValue("a", Some("b"), List("c")))
        verifyExtractionRoundTrip(WithEnumValue("a", None, List()))
      }
      "incorrect enum for string" in {
        verifyValidation[WithEnumValue](JObject("a" -> JString("b"), "c" -> JArray(Nil)), Left(List(ValidationError("a", JString("b"), EnumValueMismatch(List(JString("a")))))))
        verifyValidation[WithEnumValue](JObject("a" -> JString("a"), "c" -> JArray(List(JString("b")))), Left(List(ValidationError("c.0", JString("b"), EnumValueMismatch(List(JString("c")))))))
      }
    }
    "Synthetic properties" - {
      "Are ignored" in {
        verifyExtractionRoundTrip(WithSyntheticProperties())
        verifyValidation[WithSyntheticProperties](JObject(), Right(WithSyntheticProperties()))
        verifyValidation[WithSyntheticProperties](JObject(List("field2" -> JArray(List(JBool(true))), "field1" -> JBool(true))), Right(WithSyntheticProperties()))
      }
    }
    "Traits" - {
      "Decides on appropriate trait implementation automatically if determinable from required fields" in {
        verifyExtractionRoundTrip[EasilyDecidableTrait](NonEmpty("hello"))
        verifyExtractionRoundTrip[EasilyDecidableTrait](Empty())
      }
      "Fails gracefully when implementation is non-decidable" in {
        try {
          verifyExtractionRoundTrip[Traits](ImplA())
          fail("Shouldn't succeed")
        } catch {
          case e: TooManyMatchingCasesException =>
            e.cases.length should equal(2)
            e.path should equal("")
        }
      }
      "Respects @Discriminator" - {
        "Existence of @Discriminator field" in {
          verifyExtractionRoundTrip[DiscriminatorResolving](SomethingElse("hello"))
        }
        "@EnumValue annotation in @Discriminator field" in {
          verifyExtractionRoundTrip[DiscriminatorResolving](TypeA())
          verifyExtractionRoundTrip[DiscriminatorResolving](Type2())
        }
        "@EnumValue annotation in optional @Discriminator field" in {
          verifyExtractionRoundTrip[DiscriminatorResolving](TypeC())
          verifyExtractionRoundTrip[DiscriminatorResolving](TypeD())
        }
      }
    }
    "JValues" - {
      "JValue" in {
        verifyExtractionRoundTrip[JValue](JObject())
      }
      "JObject" in {
        verifyExtractionRoundTrip[JObject](JObject())
      }
      "JArray" in {
        verifyExtractionRoundTrip[JArray](JArray(List()))
      }
    }
    "Validation errors" - {
      "are serializable" in {
        // Verify this by constructing a Schema for ValidationError
        val schema = JsonMethods.pretty(SchemaToJson.toJsonSchema(SchemaFactory.default.createSchema[ValidationError]))
        //println(schema)
      }

      "case NotAnyOf" in {
        verifyExtractionRoundTrip(NotAnyOf(Map("foo" -> List("a", "b"))))
      }
    }
  }

  private def verifyValidation[T: ru.TypeTag](input: JValue, expectedResult: Either[List[ValidationError], AnyRef], context: ExtractionContext = ExtractionContext(SchemaFactory.default)) = {
    implicit val c = context
    SchemaValidatingExtractor.extract[T](input) should equal(expectedResult)
  }

  private def verifyExtractionRoundTrip[T](input: T)(implicit tag: ru.TypeTag[T]): T = {
    implicit val context = ExtractionContext(SchemaFactory.default)
    val json = Serializer.serialize(input, SerializationContext(SchemaFactory.default))
    val result = SchemaValidatingExtractor.extract[T](JsonMethods.compact(json))
    result should equal(Right(input))
    result.right.get
  }
}

trait EasilyDecidableTrait
case class NonEmpty(value: String) extends EasilyDecidableTrait
case class Empty() extends EasilyDecidableTrait


trait DiscriminatorResolving
case class TypeA(
  @Discriminator @EnumValue("a") `type`: String = "a"
) extends DiscriminatorResolving
case class TypeB(
  @Discriminator @EnumValue("b") `type`: String = "b"
) extends DiscriminatorResolving

case class TypeC(
  @Discriminator @EnumValue("c") optional: Option[String] = Some("c")
) extends DiscriminatorResolving

case class TypeD(
  @Discriminator @EnumValue("d") optional: Option[String] = Some("d")
) extends DiscriminatorResolving

case class Type1(
  @Discriminator @EnumValue(1) `type`: Int = 1
) extends DiscriminatorResolving

case class Type2(
  @Discriminator @EnumValue(2) `type`: Int = 2
) extends DiscriminatorResolving

case class SomethingElse(
  // Existence of this field used for detection
  @Discriminator field: String
) extends DiscriminatorResolving


case class MoreNumbers(i: Int, f: Float, l: Long, d: Double, bd : BigDecimal, bi: BigInt)
case class JavaNumbers(i: Integer, f: java.lang.Float, l: java.lang.Long, d: java.lang.Double, bd : java.math.BigDecimal)
case class BigNumbers(bi: BigInt, bd : BigDecimal)
case class MoreNumbersInLists(i: List[Int], f: List[Float], l: List[Long], d: List[Double], bd : List[BigDecimal], bi: List[BigInt])
case class OptionalNumbers(i: Option[Int], f: Option[Float], l: Option[Long], d: Option[Double], bd : Option[BigDecimal], bi: Option[BigInt])