package xyz.hyperreal.rdb

import scala.collection.mutable.{ArrayBuffer, HashSet, HashMap, ListBuffer}


class Connection {

	val baseRelations = new HashMap[String, BaseRelation]

	def executeStatement( statement: String ): Result = {
		val p = new RQLParser
		val ast = p.parseFromString( statement, p.statement )

		ast match {
			case InsertTuplesetStatement( target, tupleset ) =>
				baseRelations get target.name match {
					case None => problem( target.pos, "base relation cannot be created from tuple set" )
					case Some( base ) =>
						val types = base.header map (_.typ) toArray
						val body = evalTupleset( types, tupleset )

						base.insertTupleset( body )
				}
			case InsertRelationStatement( target, relation ) =>
				val src = evalRelation( relation )
				val dst =
					baseRelations get target.name match {
						case None =>
							val base = new BaseRelation( target.name, src.header )

							baseRelations(target.name) = base
							base
						case Some( base ) =>
							if (!src.headerSet.subsetOf( base.headerSet ))
								problem( relation.pos, "attributes must be a subset of target" )

							base
					}

				dst.insertRelation( src )
			case r: RelationExpression =>
				RelationResult( evalRelation(r) )
		}
	}

	def evalTupleset( types: Array[Type], data: List[List[ValueExpression]] ): List[Vector[AnyRef]] = {
		val body = new ArrayBuffer[Vector[AnyRef]]

		for (r <- data) {
			val row = new ArrayBuffer[AnyRef]

			if (r.length < types.length)
				problem( r.last.pos, "unexpected last value (row too short)")

			for ((v, i) <- r zipWithIndex) {
				if (i == types.length)
					problem( v.pos, "too many values")

				var x = evalExpression( v )

				val y = x match {
					case _: Mark =>
					case a: java.lang.Integer =>
						types(i) match {
							case null => types(i) = IntegerType
							case IntegerType =>
							case FloatType =>
								x = a.toDouble.asInstanceOf[Number]
							case t => problem( v.pos, s"expected $t, not integer" )
						}
					case a: String =>
						types(i) match {
							case null => types( i ) = StringType
							case StringType =>
							case t => problem( v.pos, s"expected $t, not string" )
						}
				}

				row += x
			}

			body += row.toVector
		}

		body.toList
	}

	def evalRelation( ast: RelationExpression ): Relation = {
		ast match {
			case VariableRelationExpression( Ident(p, n) ) =>
				baseRelations get n match {
					case None => problem( p, "unknown base relation" )
					case Some( r ) => r
				}
			case ProjectionRelationExpression( relation, columns ) =>
				val rel = evalRelation( relation )
				val s = new HashSet[String]
				val cs = new ListBuffer[String]

				for (Ident( p, n ) <- columns)
					if (!rel.columnNameMap.contains( n ))
						problem( p, "unknown column name" )
					else if (s(n))
						problem( p, "duplicate column name" )
					else {
						s += n
						cs += n
					}

				new ProjectionRelation( rel, cs toList )
			case ListRelationExpression( columns, data ) =>
				var hset = Set[String]()

				for (ColumnSpec( Ident(p, n), _, _, pk ) <- columns)
					if (hset(n))
						problem( p, s"duplicate $n" )
					else
						hset += n

				val types: Array[Type] =
					columns map {
						case ColumnSpec( _, _, None, pk ) => null
						case ColumnSpec( _, p, Some(t), pk ) => Type.fromSpec( p, t )
					} toArray
				val body =
					if (data isEmpty)
						types indexOf null match {
							case -1 => Nil
							case ind => problem( columns(ind).typepos, "missing type specification in empty relation" )
						}
					else
						evalTupleset( types, data )

				val header =
					columns zip types map {
						case (ColumnSpec( _, p, _, pk ), null) => problem( p, "missing type specification in relation with missing values" )
						case (ColumnSpec( Ident(_, n), _ , _, pk), t) => Column( n, t )
					}

				ListRelation( header toIndexedSeq, body )
		}
	}

	def evalExpression( ast: ValueExpression ): AnyRef = {
		ast match {
			case NumberLit( n ) => n.toDouble.asInstanceOf[Number]
			case IntegerLit( n ) => n.toInt.asInstanceOf[Number]
			case StringLit( s ) => s
			case MarkLit( m ) => m
		}
	}

	def evalLogical() {}
}

trait Result
case class InsertResult( auto: List[Map[String, AnyRef]], count: Int ) extends Result
case class RelationResult( relation: Relation ) extends Result