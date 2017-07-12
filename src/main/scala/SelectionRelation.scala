package xyz.hyperreal.rdb

import java.util.NoSuchElementException


class SelectionRelation( conn: Connection, relation: Relation, condition: LogicalResult ) extends AbstractRelation {

	def metadata = relation.metadata

	def iterator = relation.iterator filter (conn.evalCondition( _, condition ) == TRUE)

//	def iterator = {
//		new Iterator[Tuple] {
//			val it = relation.iterator
//			var row: Tuple = _
//
//			def hasNext: Boolean =
//				if (row ne null)
//					true
//				else
//					if (!it.hasNext)
//						false
//					else {
//						row = it.next
//
//						if (conn.evalCondition( row, condition ))
//							true
//						else {
//							row = null
//							hasNext
//						}
//					}
//
//			def next = {
//				if (hasNext) {
//					val res = row
//
//					row = null
//					res
//				} else
//					throw new NoSuchElementException( "no more rows" )
//			}
//		}
//	}

}