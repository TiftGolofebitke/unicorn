package com.adp.cdg

import java.util.Date
import scala.language.dynamics
import scala.language.implicitConversions
import com.adp.cdg.store.DataSet

/**
 * A document can be regarded as a JSON object with a key.
 */
class Document(id: String) extends Dynamic {
  private val RootAttributeFamily = "cdg.attributes"
  private val RelationshipFamily  = "cdg.relationships"
  private val RelationshipKeyInfix  = "-->"

  /**
   * The column family of attributes. Note that a document may be
   * a child of top document and thus the attributeFamily is different.
   */
  private var attributeFamily = RootAttributeFamily
  /**
   * The database that this document binds to.
   */
  private var dataset: Option[DataSet] = None
  
  /**
   * Document attributes.
   */
  private lazy val attributes = collection.mutable.Map[String, JsonValue]()
  /**
   * The relationships to other documents.
   * The key is the (relationship, doc). The value is any JSON object
   * associated with the relationship.
   */
  private lazy val links = collection.mutable.Map[(String, String), JsonValue]()
  /**
   * Updates to commit into database.
   */
  private lazy val updates = collection.mutable.Map[(String, String), Option[JsonValue]]()

  override def toString = {
    val s = id + " = " + JsonObjectValue(attributes).toString("", ",\n")
    if (links.isEmpty) s
    else
      s + "\nrelationships = " +
      JsonObjectValue(links.map { case (key, value) => (relationshipColumnKey(key._1, key._2), value) }).toString("", ",\n")
  }
  
  /**
   * Returns the JSON object.
   */
  def json = JsonObjectValue(attributes)

  /**
   * Returns the value of a field if it exists or None.
   */
  def apply(key: String): JsonValue = {
    if (attributes.contains(key))
      attributes(key)
    else
      JsonUndefinedValue()
  }
  
  def selectDynamic(key: String): JsonValue = {
    apply(key)
  }
  
  /**
   * Removes all attributes. If commit immediately after clear,
   * all attributes will be deleted from data set.
   */
  def clear: Document = {
    attributes.keySet.foreach { key => remove(key) }
    this
  }
  
  /**
   * Removes a field.
   */
  def remove(key: String): Option[JsonValue] = {
    val value = attributes.remove(key)
    if (value.isDefined) remove(attributeFamily, key, value.get)
    value
  }
  
  /**
   * Recursively removes the key-value pairs.
   */
  private def remove(columnFamily: String, key: String, value: JsonValue): Unit = {
    updates((columnFamily, key)) = None
    
    value match {
      case JsonObjectValue(obj) =>
        val family = columnFamily + "." + key
        obj.foreach {case (k, v) => remove(family, k, v)}
        
      case JsonArrayValue(array) =>
        val family = columnFamily + "." + key
        array.zipWithIndex foreach {case (e, i) => remove(family, i.toString, e)}
        
      case _ => ()
    }    
  }

  /**
   * Update a field.
   */
  def update(key: String, value: JsonValue): Document = {
    attributes(key) = value
    logUpdate(attributeFamily, key, value)
    this
  }
 
  /**
   * Recursively records the mutations.
   */
  private def logUpdate(columnFamily: String, key: String, value: JsonValue): Unit = {
    value match {
      case JsonUndefinedValue() => remove(key)
      case _ => updates((columnFamily, key)) = Some(value)
    }
    
    value match {
      case JsonObjectValue(obj) =>
        val family = columnFamily + "." + key
        obj.foreach {case (k, v) => logUpdate(family, k, v)}
        
      case JsonArrayValue(array) =>
        val family = columnFamily + "." + key
        array.zipWithIndex foreach {case (e, i) => logUpdate(family, i.toString, e)}
        
      case _ => ()
    }    
  }
  
  /**
   * Update a field with boolean value.
   */
  def update(key: String, value: Boolean) {
    update(key, JsonBoolValue(value))
  }
   
  /**
   * Update a field with boolean array.
   */
  def update(key: String, values: Array[Boolean]) {
    val array: Array[JsonValue] = values.map {e => JsonBoolValue(e) }
    update(key, JsonArrayValue(array))
  }
   
  /**
   * Update a field with date value.
   */
  def update(key: String, value: Date) {
    update(key, JsonDateValue(value))
  }
   
  /**
   * Update a field with date array.
   */
  def update(key: String, values: Array[Date]) {
    val array: Array[JsonValue] = values.map {e => JsonDateValue(e) }
    update(key, JsonArrayValue(array))
  }
   
  /**
   * Update a field with int value.
   */
  def update(key: String, value: Int) {
    update(key, JsonIntValue(value))
  }
   
  /**
   * Update a field with int array.
   */
  def update(key: String, values: Array[Int]) {
    val array: Array[JsonValue] = values.map {e => JsonIntValue(e) }
    update(key, JsonArrayValue(array))
  }
   
  /**
   * Update a field with double value.
   */
  def update(key: String, value: Double) {
    update(key, JsonDoubleValue(value))
  }
   
  /**
   * Update a field with double array.
   */
  def update(key: String, values: Array[Double]) {
    val array: Array[JsonValue] = values.map {e => JsonDoubleValue(e) }
    update(key, JsonArrayValue(array))
  }
   
  /**
   * Update a field with string value.
   */
  def update(key: String, value: String) {
    update(key, JsonStringValue(value))
  }
   
  /**
   * Update a field with string array.
   */
  def update(key: String, values: Array[String]) {
    val array: Array[JsonValue] = values.map {e => JsonStringValue(e) }
    update(key, JsonArrayValue(array))
  }
   
  /**
   * Update a field with array value.
   */
  def update(key: String, value: Array[JsonValue]) {
    update(key, JsonArrayValue(value))
  }
   
  /**
   * Update a field with object map value.
   */
  def update(key: String, value: collection.mutable.Map[String, JsonValue]) {
    update(key, JsonObjectValue(value))
  }
   
  /**
   * Update a field with another document.
   */
  def update(key: String, value: Document) {
    update(key, value.json)
  }
   
  /**
   * Update a field with document array.
   */
  def update(key: String, values: Array[Document]) {
    val array: Array[JsonValue] = values.map {e => e.json }
    update(key, JsonArrayValue(array))
  }
    
  def updateDynamic(key: String)(value: Any) {
    value match {
      case value: String => update(key, value)
      case value: Int => update(key, value)
      case value: Double => update(key, value)
      case value: Boolean => update(key, value)
      case value: Date => update(key, value)
      case value: JsonValue => update(key, value)
      case Some(value: JsonValue) => update(key, value)
      case value: Document => update(key, value)
      case value: Array[String] => update(key, value)
      case value: Array[Int] => update(key, value)
      case value: Array[Double] => update(key, value)
      case value: Array[Boolean] => update(key, value)
      case value: Array[Date] => update(key, value)
      case value: Array[JsonValue] => update(key, value)
      case value: Array[Document] => update(key, value)
      case null | None => remove(key) 
      case _ => throw new IllegalArgumentException("Unsupport JSON value type")
    }
  }
  
  /**
   * Loads/Reloads this document.
   */
  def refresh: Document = {
    dataset match {
      case None => throw new IllegalStateException("Document is not binding to a dataset")
      case Some(context) =>
        parseObject(context, attributeFamily, attributes)
        parseRelationships(context, RelationshipFamily, links)
    }
    
    this
  }
  
  /**
   * Loads this document from the given database.
   */
  def of(context: DataSet): Document = {
    dataset = Some(context)
    refresh
  }
  
  /**
   * Parses the byte array to a JSON value.
   */
  private def parse(context: DataSet, columnFamily: String, key: String, value: Array[Byte]): JsonValue = {
    val s = new String(value, "UTF-8")
    s.substring(0, 2) match {
      case JsonBoolValue.prefix => JsonBoolValue(s)
      case JsonDateValue.prefix => JsonDateValue(s)
      case JsonIntValue.prefix  => JsonIntValue(s)
      case JsonDoubleValue.prefix => JsonDoubleValue(s)
      case JsonStringValue.prefix => JsonStringValue.valueOf(s)
      case JsonObjectValue.prefix =>
        val family = columnFamily + "." + key
        val fields = JsonObjectValue(s)
        val doc = Document(id).from(context, family).select(fields: _*)
        doc.json
      case JsonArrayValue.prefix  =>
        val family = columnFamily + "." + key
        val size = JsonArrayValue(s)
        val array = parseArray(context, family, size)
        JsonArrayValue(array)
      case _ => JsonStringValue(s)
    }    
  }
  
  /**
   * Parses the JSON object/map into this document.
   */
  private def parseObject(context: DataSet, columnFamily: String, map: collection.mutable.Map[String, JsonValue]): Unit = {
    val kv = context.get(id, columnFamily)
    kv.foreach { case(key, value) => map(key) = parse(context, columnFamily, key, value) }
  }
  
  /**
   * Parses a column family (column qualifiers must be integers starting from 0) to an array.
   */
  private def parseArray(context: DataSet, columnFamily: String, size: Int): Array[JsonValue] = {
    val array = new Array[JsonValue](size)
    val kv = context.get(id, columnFamily)
    kv.foreach { case(key, value) => array(key.toInt) = parse(context, columnFamily, key, value) }
    array
  }
  
  /**
   * Parses the JSON object/map into this document.
   */
  private def parseRelationships(context: DataSet, columnFamily: String, map: collection.mutable.Map[(String, String), JsonValue]): Unit = {
    val kv = context.get(id, columnFamily)
    kv.foreach { case(key, value) =>
      val token = key.split(RelationshipKeyInfix)
      if (token.length == 2)
        map((token(0), token(1))) = parse(context, columnFamily, key, value)
    }
  }
  
  /**
   * Sets the context of this document (i.e. data set and column family).
   */
  def from(context: DataSet, columnFamily: String = RootAttributeFamily): Document = {
    dataset = Some(context)
    attributeFamily = columnFamily
    this
  }
  
  /**
   * Loads given fields rather than the whole document.
   */
  def select(fields: String*): Document = {
    dataset match {
      case None => throw new IllegalStateException("Document is not binding to a dataset")
      case Some(context) =>
        context.get(id, attributeFamily, fields: _*).
          foreach { case (key, value) => attributes(key) = parse(context, attributeFamily, key, value) }
        this
    }
  }
  
  /**
   * Commits changes to data set.
   */
  def commit {
    if (updates.isEmpty) return
    
    dataset match {
      case None => throw new IllegalStateException("Document is not binding to a dataset")
      case Some(context) => {
        updates.foreach { case(familyCol, value) =>
          value match {
            case None => context.remove(id, familyCol._1, familyCol._2)
            case Some(value) => context.put(id, familyCol._1, familyCol._2, value.bytes)
          }
        }
        context.commit
        updates.clear
      }
    }
  }
  
  /**
   * Writes this documents (only updated/deleted fields) to the data set.
   */
  def into(context: DataSet) {
    dataset = Some(context)
    commit
  }
  
  /**
   * Returns all neighbors of given relationships.
   */
  def neighbors(relationship: String): Map[Document, JsonValue] = {
    var nodes = List[(Document, JsonValue)]()
    
    links.foreach { case ((relation, id), value) if relation == relationship =>
      val doc = Document(id)
      dataset match {
        case Some(context) => doc.from(context)
        case _ => ()
      }
      
      nodes = (doc, value) :: nodes
    }
    
    nodes.toMap
  }
  
  /**
   * Returns the value of a relationship if it exists or None.
   */
  def apply(relationship: String, doc: String): JsonValue = {
    if (links.contains((relationship, doc)))
      links((relationship, doc))
    else
      JsonUndefinedValue()
  }
  
  /**
   * Update a relationship.
   */
  def update(relationship: String, doc: String, value: JsonValue): Document = {
    if (value == null) {
      remove(relationship, doc)
    } else {
      links((relationship, doc)) = value
      logUpdate(RelationshipFamily, relationshipColumnKey(relationship, doc), value)
    }
    
    this
  }
  
  def update(relationship: String, doc: String, value: Boolean): Document = {
    update(relationship, doc, JsonBoolValue(value))
  }
   
  def update(relationship: String, doc: String, value: Date): Document = {
    update(relationship, doc, JsonDateValue(value))
  }
   
  def update(relationship: String, doc: String, value: Int): Document = {
    update(relationship, doc, JsonIntValue(value))
  }
   
  def update(relationship: String, doc: String, value: Double): Document = {
    update(relationship, doc, JsonDoubleValue(value))
  }
   
  def update(relationship: String, doc: String, value: String): Document = {
    update(relationship, doc, JsonStringValue(value))
  }
   
  def update(relationship: String, doc: String, value: Array[JsonValue]): Document = {
    update(relationship, doc, JsonArrayValue(value))
  }
   
  def update(relationship: String, doc: String, value: Document): Document = {
    update(relationship, doc, value.json)
  }
    
  /**
   * Removes a relationship.
   */
  def remove(relationship: String, doc: String): Document = {
    val value = links.remove((relationship, doc))
    if (value.isDefined) remove(RelationshipFamily, relationshipColumnKey(relationship, doc), value.get)
    this
  }
   
  /**
   * Returns all relationships to a given neighbor.
   */
  def relationships(doc: String): Map[String, JsonValue] = {
    var edges = List[(String, JsonValue)]()

    links.foreach { case ((relation, id), value) if id == doc =>
        edges = (relation, value) :: edges
    }

    edges.toMap
  }
  
  private def relationshipColumnKey(relationship: String, doc: String) =
    relationship + RelationshipKeyInfix + doc
}

object Document {
  def apply(id: String): Document = new Document(id)
}

object DocumentImplicits {
  implicit def String2Document (id: String) = new Document(id)
}
