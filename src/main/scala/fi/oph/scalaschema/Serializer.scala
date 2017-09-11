package fi.oph.scalaschema

import java.time.LocalDate

import fi.oph.scalaschema.extraction.SchemaNotFoundException
import org.json4s.JsonAST._
import org.json4s.{DefaultFormats, Extraction, Formats, JValue}

object Serializer {
  implicit val format: Formats = new DefaultFormats() {}

  def serialize[T](obj: T)(implicit context: SerializationContext): JValue = {
    val className = obj.getClass.getName
    context.rootSchema.getSchema(className) match {
      case Some(schema) => serializeWithSchema(obj, schema)
      case _ => throw new SchemaNotFoundException("", className)
    }
  }

  private def serializeWithSchema(x: Any, schema: Schema)(implicit context: SerializationContext): JValue = {
    schema match {
      case s: ClassSchema => serializeObject(s, x)
      case s: ClassRefSchema =>
        val actualSchema = context.rootSchema.getSchema(s.fullClassName).getOrElse(throw new SchemaNotFoundException("", s.fullClassName))
        serializeWithSchema(x, actualSchema)
      case s: AnyOfSchema =>
        s.findAlternative(x) match {
          case Some(foundSchema) => serializeWithSchema(x, foundSchema)
          case _ => throw new RuntimeException("Schema not found for " + x + " as an implementation of " + s.fullClassName)
        }
      case s: OptionalSchema => serializeOption(s, x)
      case s: ListSchema => serializeList(s, x)
      case s: StringSchema => serializeString(s, x)
      case s: NumberSchema => serializeNumber(s, x)
      case s: DateSchema => serializeDate(s, x)
      case s: BooleanSchema => serializeBoolean(s, x)
    }
  }

  private def serializeOption(s: OptionalSchema, x: Any)(implicit context: SerializationContext): JValue = x match {
    case Some(x) => serializeWithSchema(x, s.itemSchema)
    case None => JNothing
    case x => serializeWithSchema(x, s.itemSchema)
  }

  private def serializeList(s: ListSchema, x: Any)(implicit context: SerializationContext): JValue = x match {
    case xs: List[_] => JArray(xs.map { x => serializeWithSchema(x, s.itemSchema)})
    case _ => throw new RuntimeException("Not a List: " + x)
  }

  private def serializeObject(s: ClassSchema, x: Any)(implicit context: SerializationContext): JValue = JObject(s.properties.flatMap { p =>
    val value = s.getPropertyValue(p, x.asInstanceOf[AnyRef])
    serializeWithSchema(value, p.schema) match {
      case JNothing => None
      case jValue => Some(JField(p.key, jValue))
    }
  })

  private def serializeString(s: StringSchema, x: Any): JValue = x match {
    case x: String => JString(x)
    case _ => throw new RuntimeException("Not a String: " + x)
  }

  private def serializeNumber(s: NumberSchema, x: Any): JValue = x match {
    case x: Number => Extraction.decompose(x)
    case _ => throw new RuntimeException("Not a Number: " + x)
  }

  private def serializeDate(s: DateSchema, x: Any): JValue = x match {
    case x: LocalDate => JString(x.toString)
    case _ => throw new RuntimeException("Not a LocalDate: " + x)
  }

  private def serializeBoolean(s: BooleanSchema, x: Any): JValue = x match {
    case x: Boolean => JBool(x)
    case _ => throw new RuntimeException("Not a Boolean: " + x)
  }
}

case class SerializationContext(rootSchema: Schema)