package org.overviewproject.jobhandler.filegroup.task

import java.io.ByteArrayOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.util.PDFTextStripper
import scala.collection.JavaConverters._
import scala.concurrent.Future
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.util.Textify
import scala.collection.IterableView
import scala.collection.SeqView
import org.apache.pdfbox.io.RandomAccessFile
import java.io.File
import scala.util.control.Exception.ultimately
import scala.util.Try

class PdfBoxDocument(file: File) extends PdfDocument {
  private val TempFilePrefix = "overview-pdfbox-"
  private val TempFileExtension = ".tmp"

  private val textStripper = new FontDetectingTextStripper
  private val document: PDDocument = init(file)

  override def pages: SeqView[PdfPage, Seq[_]] = splitPages.map { p =>
    val data = getData(p)
    val text = getText(p)

    p.close

    PdfPage(data, text)
  }

  override def text: String = getText(document)

  /**
   * Text extraction and font detection is combined in one call 
   * to avoid having to read the file twice. 
   */
  def textWithFonts: Either[String, String] = {
    val t = getText(document)
    Either.cond(textStripper.foundFonts, t, t)
  }
  
  override def close(): Unit = document.close()

  private def init(file: File): PDDocument = {
    val tempFile = File.createTempFile(TempFilePrefix, TempFileExtension)
    val scratchFile = new RandomAccessFile(tempFile, "rw")

    ultimately(tempFile.delete) {
      PDDocument.loadNonSeq(file, scratchFile)
    }
  }

  // Use a view to prevent all page data from being loaded into memory at once
  private def splitPages: SeqView[PDDocument, Seq[_]] =
    document.getDocumentCatalog.getAllPages().asScala.view.map { p =>
      createDocument(p.asInstanceOf[PDPage])
    }

  private def createDocument(page: PDPage): PDDocument = {

    val pageDocument = new PDDocument()
    pageDocument.setDocumentInformation(document.getDocumentInformation())
    pageDocument.getDocumentCatalog().setViewerPreferences(
      document.getDocumentCatalog().getViewerPreferences())

    val pageInDocument = pageDocument.importPage(page)
    pageInDocument.setCropBox(page.findCropBox());
    pageInDocument.setMediaBox(page.findMediaBox());

    pageInDocument.setResources(page.getResources());
    pageInDocument.setRotation(page.findRotation());

    pageDocument
  }

  private def getData(page: PDDocument): Array[Byte] = {
    val outputStream = new ByteArrayOutputStream()
    page.save(outputStream)
    outputStream.close()

    outputStream.toByteArray()
  }

  private def getText(document: PDDocument): String = {
    val rawText: String = textStripper.getText(document)

    Textify(rawText)
  }
  
  private class FontDetectingTextStripper extends PDFTextStripper {
    import org.apache.pdfbox.util.TextPosition
    
    private var detectedFont: Boolean = false
   
    def foundFonts: Boolean = detectedFont
    
    override protected def writeString(text: String, textPositions: java.util.List[TextPosition]): Unit = {
      super.writeString(text, textPositions)

      if (!detectedFont) {
        val fontNames = textPositions.asScala.map(_.getFont().getBaseFont())
        if (fontNames.nonEmpty) detectedFont = true
      }

    }

  }
  
}

object PdfBoxDocument {
  def loadFromLocation(location: String): Future[PdfDocument] =
    BlobStorage.withBlobInTempFile(location)(loadFromFile)

  def loadFromFile(file: File): Future[PdfDocument] = Future.fromTry(
    Try(new PdfBoxDocument(file)))

}