package models.export.rows

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}

import org.overviewproject.models.Tag

class DocumentsWithStringTagsSpec extends Specification with FutureAwaits with DefaultAwaitTimeout {
  trait BaseScope extends Scope {
    val factory = org.overviewproject.test.factories.PodoFactory
    def documents: Enumerator[DocumentForCsvExport]
    val tags: Seq[Tag] = Seq()
    lazy val rows: Rows = DocumentsWithStringTags(documents, tags)
    lazy val rowList: List[Array[String]] = await(rows.rows.run(Iteratee.getChunks))
    def headRow = rowList.head
  }

  trait OneDocumentScope extends BaseScope {
    val sampleDocument = DocumentForCsvExport("suppliedId", "title", "text", "url", Seq())
    val document = sampleDocument
    override def documents = Enumerator(document)
  }

  "ExportDocumentsWithStringTags" should {
    "export tags" in new OneDocumentScope {
      override val tags = Seq(
        factory.tag(id=2L, name="aaa"), // IDs out of order so we test ordering
        factory.tag(id=3L, name="bbb"),
        factory.tag(id=1L, name="ccc")
      )
      override val document = sampleDocument.copy(tagIds=Seq(1L, 3L))
      headRow(4) must beEqualTo("bbb,ccc")
    }

    "export suppliedId" in new OneDocumentScope {
      override val document = sampleDocument.copy(suppliedId="foobar")
      headRow(0) must beEqualTo("foobar")
    }

    "export title" in new OneDocumentScope {
      override val document = sampleDocument.copy(title="foobar")
      headRow(1).toString must beEqualTo("foobar")
    }

    "export url" in new OneDocumentScope {
      override val document = sampleDocument.copy(url="foobar")
      headRow(3) must beEqualTo("foobar")
    }

    "export text" in new OneDocumentScope {
      override val document = sampleDocument.copy(text="foobar")
      headRow(2) must beEqualTo("foobar")
    }
  }
}
