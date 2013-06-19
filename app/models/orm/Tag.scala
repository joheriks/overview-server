package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.overviewproject.postgres.SquerylEntrypoint._

case class Tag(
  val id: Long = 0L,
  @Column("document_set_id") val documentSetId: Long,
  val name: String,
  val color: String) extends KeyedEntity[Long] {

  override def isPersisted(): Boolean = id != 0L
}
