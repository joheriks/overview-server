package controllers

import play._
import play.libs.WS
import play.libs.F.Promise
import play.libs.F.Function;
import play.libs.Json

import models._

import scala.collection.JavaConversions._  // for iteration through documentReferences JsonNode -- and other things here?



class DocumentSetIndexer(var documentSet:DocumentSet) {

  // Query documentCloud and create one document object for each doc returned by the query
  def createDocuments() = {
    val queryString = documentSet.query
    val documentCloudQuery = "http://www.documentcloud.org/api/search.json"
    
    // Query DocumentCloud for all docs matching query string
    val DCcall = WS.url(documentCloudQuery).setQueryParameter("q", queryString).get();
    val response = DCcall.get();  // blocks until result comes back. but does it tie up the thread? not sure, async() may be better
  
    // Iterate over returned docs, each one described by a block of JSON
    val documentReferences  = response.asJson().get("documents")
    for (document <- documentReferences) { 
      
      var title = document.get("title").toString()
      title = title.replace("\"", "")
      
      var canonicalUrl = document.get("canonical_url").toString()
  
      canonicalUrl = canonicalUrl.replace("\"", "")
      var textUrl = document.get("resources").get("text").toString()
      textUrl = textUrl.substring(1,textUrl.length-1) // remove quotes
                      
      val newDoc = new Document(documentSet, title, textUrl, canonicalUrl)
      newDoc.save()
      documentSet.documents.add(newDoc)
    }
   
    documentSet.update();     
  }
  
  // Index a collection of documents, TF-IDF style
  def indexDocuments() = {
    
    val N = documentSet.documents.size
    var idf = Map[String, Float]()            // term -> number of docs containing term
    var tf = Map[Long, Map[String, Float]]()   // docuent ID -> (term -> frequency)
    
    for (document <- documentSet.documents) {

      // Get the doument text
      val text = WS.url(document.textUrl).get().get().getBody()
      //println(text)

      // Turn into tokens
      val tokens = Lexer.make_terms(text)
      if (tokens.size > 0) {
        
        // count how many times each token appears in this doc (term frequency)      
        var termcounts = Map[String, Int]()
        for (t <- tokens) {
          val prev_count = if (termcounts.contains(t)) termcounts(t) else 0
          termcounts += (t -> (prev_count + 1))
        }
        
        // divide out document length to go from term count to term frequency
        val termfreqs = termcounts.mapValues(count => count/tokens.size.toFloat) 
        tf += (document.id.toLong -> termfreqs)
         
        // for each unique term in this doc, update how many docs each term appears in (doc frequency)
        for (t <- termcounts.keys) {
          val prev_count = if (idf.contains(t)) idf(t) else 0
          idf += (t -> (prev_count + 1))
        }
      }      
    }
    
    // Now that we've read all documents, transform the document count into idf
    idf = idf.mapValues(count => math.log10(N / count).toFloat)
    
    // Now we have to run over the stored TF values one more time, multiplying them by the IDF values for each term, and normalizing
    for ((docid, tf) <- tf) {
      val tfidf = tf.map({case (term,tf) => (term, tf * idf(term))})
      val length = math.sqrt(tfidf.values.fold(0f) { (sumsq, weight) => sumsq + weight*weight }).toFloat
      val normalized = tfidf.mapValues(weight => weight/length)
      
      println("------------")

      println(normalized.toList.sorted)
    }
    
  }
}