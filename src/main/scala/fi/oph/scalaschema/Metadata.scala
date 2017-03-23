package fi.oph.scalaschema

import org.json4s.JsonAST
import org.json4s.JsonAST.{JNothing, JObject, JString}

import scala.annotation.StaticAnnotation

trait Metadata extends StaticAnnotation with JsonMetadataSupport {
  def applyMetadata(x: ObjectWithMetadata[_], schemaFactory: SchemaFactory): ObjectWithMetadata[_] = x.appendMetadata(List(this.asInstanceOf[Metadata]))
  def appendMetadataToJsonSchema(obj: JObject): JObject
}

trait ObjectWithMetadata[T <: ObjectWithMetadata[T]] {
  def metadata: List[Metadata]
  def replaceMetadata(newMetadata: List[Metadata]): ObjectWithMetadata[T]
  def appendMetadata(newMetadata: List[Metadata]): ObjectWithMetadata[T] = replaceMetadata(metadata ++ newMetadata)
}

trait AnnotationSupport[M] {
  def metadataClass: Class[M]
}

trait JsonMetadataSupport {
  def appendToDescription(obj: JObject, newDescription: String): JsonAST.JObject = {
    val description = obj.\("description") match {
      case JString(s) if s.endsWith(".") => s + " " + newDescription
      case JString(s) => s + ". " + newDescription
      case JNothing => newDescription
    }
    obj.merge(JObject("description" -> JString(description)))
  }

  def addEnumValue(value: String, p: Property): Property = {
    val newSchema = p.schema match {
      case StringSchema(enumValues) =>
        StringSchema(Some(enumValues.toList.flatten ++ List(value)))
      case x: Any => throw new RuntimeException("Unexpected schema: " + x)

    }
    p.copy(schema = newSchema)
  }
}

trait RepresentationalMetadata extends Metadata {
  override def appendMetadataToJsonSchema(obj: JObject) = obj // Does not affect JSON schema
}