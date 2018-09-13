package com.flowtick.sysiphos.slick

import slick.lifted.{ AbstractTable, CanBeQueryCondition, Query }

trait SlickRepositoryBase {
  implicit class TableOps[Table <: AbstractTable[_], Element, Context[_]](query: Query[Table, Element, Context]) {
    def filterOptional[Value, Rep <: slick.lifted.Rep[_]](option: Option[Value])(f: Value => Table => Rep)(implicit wt: CanBeQueryCondition[Rep]): Query[Table, Element, Context] = {
      option.map(a => query.filter(f(a))).getOrElse(query)
    }
  }
}
