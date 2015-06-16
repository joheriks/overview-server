package org.overviewproject.models

import java.util.Date // should be java.time.LocalDateTime
import org.overviewproject.models.DocumentDisplayMethod.DocumentDisplayMethod



/** Metadata about a Document.
  */
trait DocumentHeader {
  val id: Long
  val documentSetId: Long
  val url: Option[String]
  val suppliedId: String
  val title: String
  val pageNumber: Option[Int]
  val keywords: Seq[String]
  val createdAt: Date
  val displayMethod: Option[DocumentDisplayMethod]
  val text: String

  def viewUrl: Option[String] = url

  def folderPath: Option[String] = {
    val pathDelimiterIndex = this.title.lastIndexOf("/")
    if(pathDelimiterIndex != -1) {
      Some(("/" + this.title).splitAt(pathDelimiterIndex + 1)._1)
    } else {
      None
    }
  }

  def titleProper: String = {
    val pathDelimiterIndex = this.title.lastIndexOf("/")
    if(pathDelimiterIndex != -1) {
      this.title.splitAt(pathDelimiterIndex + 1)._2
    } else {
      this.title
    }
  }
}
