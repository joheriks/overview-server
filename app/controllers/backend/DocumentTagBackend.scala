package controllers.backend

import scala.concurrent.Future

trait DocumentTagBackend extends Backend {
  /** Gives a list of Node IDs for each Document.
    *
    * There are no empty lists: they are not defined.
    *
    * The returned lists are not ordered.
    */
  def indexMany(documentIds: Seq[Long]): Future[Map[Long,Seq[Long]]]
}

trait DbDocumentTagBackend extends DocumentTagBackend with DbBackend {
  import database.api._
  import database.executionContext

  override def indexMany(documentIds: Seq[Long]) = {
    if (documentIds.isEmpty) {
      Future.successful(Map())
    } else {
      import slick.jdbc.GetResult
      implicit val rconv = GetResult(r => (r.nextLong() -> r.nextArray[Long]()))
      database.run(sql"""
        SELECT document_id, ARRAY_AGG(tag_id)
        FROM document_tag
        WHERE document_id IN (#${documentIds.mkString(",")})
        GROUP BY document_id
      """.as[(Long,Seq[Long])])
        .map(_.toMap)
    }
  }
}

object DocumentTagBackend extends DbDocumentTagBackend
