package controllers.api

import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.WithApplication
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedRequest
import controllers.backend.{DocumentBackend,SelectionBackend}
import models.pagination.{Page,PageInfo,PageRequest}
import models.{InMemorySelection,Selection}
import org.overviewproject.models.DocumentHeader

class DocumentControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val selection = InMemorySelection(Seq()) // override for a different Selection
    def buildSelection: Future[Either[Result,Selection]] = Future(Right(selection)) // override for edge cases
    val mockDocumentBackend = mock[DocumentBackend]
    val controller = new DocumentController {
      override val documentBackend = mockDocumentBackend
      override def requestToSelection(documentSetId: Long, request: ApiAuthorizedRequest[_]) = buildSelection
    }
  }

  "DocumentController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSetId = 1L
        val q = ""
        val fields = ""
        val pageRequest = PageRequest(0, 1000)
        def emptyPage[T] = Page(Seq[T](), PageInfo(pageRequest, 0))

        override lazy val request = fakeRequest("GET", "?q=" + q)
        override def action = controller.index(documentSetId, fields)

        mockDocumentBackend.index(any, any, any) returns Future.successful(emptyPage[DocumentHeader])
      }

      "return JSON with status code 200" in new IndexScope {
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no Documents" in new IndexScope {
        contentAsString(result) must /("pagination") /("total" -> 0)
      }

      "grab pageRequest from the HTTP request" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 2), false)
      }

      "set page limit to 1000" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=9999999")
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 1000), false)
      }

      "set page limit to 20 when requesting text" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=9999999")
        override val fields = "id,text"
        status(result)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 20), true)
      }

      "return an Array of IDs when fields=id" in new IndexScope {
        override val fields = "id"
        override val selection = InMemorySelection(Seq(1L, 2L, 3L))
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
        contentAsString(result) must beEqualTo("[1,2,3]")
      }

      "return the selectionId" in new IndexScope {
        override val selection = InMemorySelection(Seq(1L, 2L, 3L))
        contentAsString(result) must /("selectionId" -> selection.id.toString)
      }

      trait IndexFieldsScope extends IndexScope {
        val documents = List(
          factory.document(
            title="foo",
            keywords=Seq("foo", "bar"),
            suppliedId="supplied 1",
            text="text",
            url=Some("http://example.org"),
            pageNumber=Some(1)
          ),
          factory.document(title="", keywords=Seq(), suppliedId="", url=None)
        )
        override val selection = InMemorySelection(documents.map(_.id))
        mockDocumentBackend.index(any, any, any) returns Future.successful(
          Page(documents, PageInfo(PageRequest(0, 100), documents.length))
        )
      }

      "return some Documents when there are Documents" in new IndexFieldsScope {
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("title" -> "foo")
        json must /("items") /#(0) /("keywords") /#(0) /("foo")
        json must /("items") /#(0) /("keywords") /#(1) /("bar")
        json must /("items") /#(0) /("suppliedId" -> "supplied 1")
        json must /("items") /#(0) /("url" -> "http://example.org")
        json must /("items") /#(0) /("pageNumber" -> 1)
        // specs2 foils me on this one:
        // json must not /("items") /#(0) /("text")

        json must /("items") /#(1) /("id" -> documents(1).id)
        json must /("items") /#(1) /("title" -> "")
        // I can't get these past specs2:
        // json must /("items") /#(1) /("keywords" -> beEmpty[Seq[Any]])
        // json must not /("items") /#(1) /("suppliedId")
        // json must not /("items") /#(1) /("url")
        // json must not /("items") /#(1) /("text")
      }

      "return specified fields (1/2)" in new IndexFieldsScope {
        override val fields = "id,documentSetId,url,suppliedId,title"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("documentSetId" -> documents(0).documentSetId)
        json must /("items") /#(0) /("url" -> "http://example.org")
        json must /("items") /#(0) /("suppliedId" -> "supplied 1")
        json must /("items") /#(0) /("title" -> "foo")
        json must not(beMatching("keywords".r))
        json must not(beMatching("pageNumber".r))
      }

      "return specified fields (2/2)" in new IndexFieldsScope {
        override val fields = "id,keywords,pageNumber"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("keywords") /#(0) /("foo")
        json must /("items") /#(0) /("keywords") /#(1) /("bar")
        json must /("items") /#(0) /("pageNumber" -> 1)
        json must not(beMatching("documentSetId".r))
        json must not(beMatching("url".r))
        json must not(beMatching("suppliedId".r))
        json must not(beMatching("title".r))
      }

      "query for text when fields includes text" in new IndexFieldsScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        override val fields = "id,text"

        status(result) must beEqualTo(OK)
        there was one(mockDocumentBackend).index(selection, PageRequest(1, 2), true)
      }

      "include text in JSON response" in new IndexFieldsScope {
        override val fields = "id,text"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("text" -> "text")
      }

      "always return id, even if it is not in the fields" in new IndexFieldsScope {
        override val fields = "suppliedId"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("suppliedId" -> "supplied 1")
        json must not(beMatching("url".r))
      }

      "ignore invalid fields" in new IndexFieldsScope {
        override val fields = "id,title,bleep"

        status(result) must beEqualTo(OK)
        val json = contentAsString(result)

        json must /("items") /#(0) /("id" -> documents(0).id)
        json must /("items") /#(0) /("title" -> "foo")
        json must not(beMatching("bleep".r))
      }

      "stream content" in new WithApplication with IndexFieldsScope {
        override lazy val request = fakeRequest("GET", "/?stream=true")

        status(result) must beEqualTo(OK)
        header("Content-Length", result) must beNone
        header("Transfer-Encoding", result) must beSome("chunked")
        header("Content-Type", result) must beSome("application/json")

        import play.api.libs.iteratee.{Enumerator,Iteratee}
        import play.api.mvc.Results
        val json = new String(await(Enumerator(contentAsBytes(result)).through(Results.dechunk).run(Iteratee.consume())), "utf-8")
        json must /("pagination") /("total" -> 2)
        json must /("items") /#(0) /("id" -> documents(0).id)
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSetId = 1L
        val documentId = 2L

        override def action = controller.show(documentSetId, documentId)
      }

      "return 404 when not found" in new ShowScope {
        mockDocumentBackend.show(documentSetId, documentId) returns Future(None)
        status(result) must beEqualTo(NOT_FOUND)
        contentType(result) must beSome("application/json")
        val json = contentAsString(result)
        json must /("message" -> s"Document $documentId not found in document set $documentSetId")
      }

      "return JSON with status code 200" in new ShowScope {
        mockDocumentBackend.show(documentSetId, documentId) returns Future(Some(factory.document(
          id=documentId,
          documentSetId=documentSetId,
          keywords=Seq("foo", "bar"),
          url=Some("http://example.org"),
          text="text",
          title="title",
          suppliedId="suppliedId"
        )))

        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")

        val json = contentAsString(result)

        json must /("id" -> documentId)
        json must not /("documentSetId")
        json must /("keywords") /#(0) /("foo")
        json must /("keywords") /#(1) /("bar")
        json must /("url" -> "http://example.org")
        json must /("title" -> "title")
        json must /("text" -> "text")
        json must /("suppliedId" -> "suppliedId")
      }
    }
  }
}
