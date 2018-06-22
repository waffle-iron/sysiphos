package com.flowtick.sysiphos.ui.vendor

import org.scalajs.jquery._
import shapeless.ops.hlist.ToTraversable
import shapeless.ops.record._
import shapeless.{ LabelledGeneric, _ }

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object DataTablesSupport {
  trait FieldNames[T] {
    def apply(): List[String]
  }

  implicit def toNames[T, Repr <: HList, KeysRepr <: HList](implicit
    gen: LabelledGeneric.Aux[T, Repr],
    keys: Keys.Aux[Repr, KeysRepr],
    traversable: ToTraversable.Aux[KeysRepr, List, Symbol]): FieldNames[T] = new FieldNames[T] {
    def apply() = keys().toList.map(_.name)
  }

  def fieldNames[T](implicit h: FieldNames[T]) = h()

  def createDataTable[T, L <: HList](elementId: String, items: Seq[T])(implicit labelledGeneric: LabelledGeneric.Aux[T, L], h: FieldNames[T]) = {
    val data: js.Array[js.Array[String]] = items.toJSArray.map(item => {
      labelledGeneric.to(item).runtimeList.map {
        case list: Seq[_] => list.mkString(",")
        case any: Any => any.toString
      }.toJSArray
    })

    val columns = h().map(name => js.Dictionary(
      "title" -> name)).toJSArray

    val params = js.Dictionary(
      "data" -> data,
      "columns" -> columns)

    jQuery(elementId).asInstanceOf[js.Dynamic].DataTable(params)
  }
}
