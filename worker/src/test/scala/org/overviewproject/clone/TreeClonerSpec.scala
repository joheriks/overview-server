package org.overviewproject.clone

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.models.tables.Trees
import org.overviewproject.test.DbSpecification

class TreeClonerSpec extends DbSpecification {
  "TreeCloner" should {
    trait TreeCloneContext extends DbScope {
      import database.api._

      val sourceDocumentSet = factory.documentSet(1L)
      val cloneDocumentSet = factory.documentSet(2L)

      val sourceRootNode = factory.node(1L << 32)
      val cloneRootNode = factory.node(2L << 32)

      val sourceTree = factory.tree(
        id = 1L << 32,
        documentSetId = sourceDocumentSet.id,
        rootNodeId = sourceRootNode.id,
        jobId=0L,
        title="title",
        documentCount=100,
        lang="lang",
        suppliedStopWords="stopwords",
        importantWords="importantwords"
      )

      DeprecatedDatabase.inTransaction {
        TreeCloner.clone(sourceDocumentSet.id, cloneDocumentSet.id)
      }

      val cloneTree = blockingDatabase.option(Trees.filter(_.documentSetId === cloneDocumentSet.id))
    }

    "clone the tree of a document set" in new TreeCloneContext {
      cloneTree.get.id must beEqualTo(2L << 32)
      cloneTree.get.documentSetId must beEqualTo(cloneDocumentSet.id)
      cloneTree.get.rootNodeId must beEqualTo(cloneRootNode.id)
      cloneTree.get.jobId must beEqualTo(0L)
      cloneTree.get.title must beEqualTo("title")
      cloneTree.get.documentCount must beEqualTo(100)
      cloneTree.get.lang must beEqualTo("lang")
      cloneTree.get.suppliedStopWords must beEqualTo("stopwords")
      cloneTree.get.importantWords must beEqualTo("importantwords")
    }
  }
}
