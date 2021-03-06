package org.overviewproject.jobhandler.filegroup.task.step

import java.io.InputStream
import java.util.UUID
import org.specs2.mock.Mockito
import scala.concurrent.Future
import org.overviewproject.blobstorage.BlobBucketId
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.jobhandler.filegroup.task.DocumentConverter
import org.overviewproject.models.File
import org.overviewproject.models.tables.Files
import org.overviewproject.test.DbSpecification
import scala.concurrent.ExecutionContext

class CreateFileWithViewSpec extends DbSpecification with Mockito {

  "CreateFileWithView" should {

    "create a File with a PDF view" in new ConvertScope {
      await(createFileWithView.execute)

      val file = findFile

      file.map(f => (f.contentsLocation, f.contentsSize, f.viewLocation, f.viewSize)) must
        beSome((contentsLocation, contentsSize, viewLocation, viewSize))
      
      file.map(_.contentsSha1.length) must beSome(20)
    }

    "return next step with File" in new ConvertScope {
      val NextStep(file) = await(createFileWithView.execute)
      
      val savedFile = findFile
      
      file.id must beEqualTo (savedFile.get.id)
    }

    trait ConvertScope extends DbScope {

      val contentsLocation = "content:location"
      val contentsSize = 100l
      val viewLocation = "view:location"
      val viewSize = 200l

      val fileGroup = factory.fileGroup()
      val upload = factory.groupedFileUpload(fileGroupId = fileGroup.id, size = contentsSize)

      val createFileWithView = new TestCreateFileWithView

      def findFile: Option[File] = {
        import database.api._
        blockingDatabase.option(Files)
      }

      class NullConverter extends DocumentConverter {
        override def withStreamAsPdf[T](guid: UUID, inputStream: InputStream)
          (f: (InputStream, Long) => T) = f(inputStream, viewSize)
      }

      case class NextStep(file: File) extends TaskStep {
        override def execute = Future.successful(this)
      }

      class TestCreateFileWithView extends CreateFileWithView {
        override protected val executor: ExecutionContext = implicitly
        
        override protected val documentSetId = 1l
        override protected val uploadedFile = upload
        override protected val nextStep = { f: File => NextStep(f) }
        
        override protected val blobStorage = smartMock[BlobStorage]
        override protected def largeObjectInputStream(oid: Long) = smartMock[InputStream]
        override protected val converter = new NullConverter
        
        blobStorage.create(
          be(BlobBucketId.FileContents),
          any,
          be_===(contentsSize)) returns Future.successful(contentsLocation)
          
        blobStorage.create(
          be(BlobBucketId.FileView),
          any,
          be_===(viewSize)) returns Future.successful(viewLocation)
          
      }


    }
  }

}
